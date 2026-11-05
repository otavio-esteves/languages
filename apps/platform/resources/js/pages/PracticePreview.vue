<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { Link } from '@inertiajs/vue3';
type Support = { id: string; kind: string; content: string; affected_modalities: string[]; reveals_answer: boolean };
type Activity = { id: string; type: string; modality: string; prompt: string; target_ids: string[]; supports: Support[]; content: { asset_id?: string; examples?: string[]; options?: string[]; pt_BR_note?: string }; expected: { accepted?: string[] }; feedback: string };
const props = defineProps<{ mission: { id: string; version: number; title: string; activities: Activity[] }; manifest: { package_hash: string; is_preview: boolean } }>();
const index = ref(0), answer = ref(''), revealed = ref<string[]>([]), busy = ref(false), message = ref(''), feedback = ref(''), recorded = ref(false), error = ref(false);
const session = ref(crypto.randomUUID());
const device = crypto.randomUUID();
let sequence = 0;
const events = ref<{ id: string; kind: string; payload: Record<string, unknown>; received: boolean }[]>([]);
const activity = computed(() => props.mission.activities[index.value]);
const finished = ref(false);
const recording = ref(false), recordingUrl = ref<string | null>(null), audioError = ref('');
let disposed=false;
let recorder: MediaRecorder | null = null;
let stream: MediaStream | null = null;
let audio: HTMLAudioElement | null = null;
const audioReady = ref(false);
const listeningFailed=ref(false);
function base() { return { schema_version: '1.0.0', id: crypto.randomUUID(), session_id: session.value, device_id: device, local_sequence: ++sequence, occurred_at: new Date().toISOString(), time_quality: 'uncertain', activity_id: activity.value.id, package_id: props.mission.id, package_version: props.mission.version, package_hash: props.manifest.package_hash, target_ids: activity.value.target_ids }; }
async function send(kind: string, payload: Record<string, unknown>) {
  const existing=events.value.find(e=>!e.received);
  if(existing && (existing.kind!==kind || existing.payload.activity_id!==payload.activity_id || existing.payload.response!==payload.response || existing.payload.support_id!==payload.support_id)) throw new Error('Reenvie primeiro o registro pendente.');
  const event=existing || { id: payload.id as string, kind, payload, received: false };if(!existing)events.value.push(event);
  const cookie = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));
  const response = await fetch(`/editorial/${kind}s/batch`, { method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'X-XSRF-TOKEN': cookie ? decodeURIComponent(cookie.split('=').slice(1).join('=')) : '' }, body: JSON.stringify({ events: [event.payload] }) });
  if (!response.ok) throw new Error('O registro não foi confirmado. Mantenha esta página aberta e tente novamente.');
  const result = await response.json();
  if (!['accepted','duplicate'].includes(result.results?.[0]?.status)) throw new Error(result.results?.[0]?.message || 'Registro não aceito.');
  const item=events.value.find(e=>e.id===event.id); if(item) item.received=true;
}
async function action(fn: () => Promise<void>) { if(busy.value) return; busy.value=true; message.value=''; try { await fn(); } catch(e) { message.value=e instanceof Error ? e.message : 'Não foi possível concluir.'; } finally { busy.value=false; } }
async function expose(support?: Support) { await send('exposure', { ...base(), support_id: support?.id || `activity-${activity.value.id}`, kind: support?.kind || 'training_use', affected_modalities: support?.affected_modalities || [activity.value.modality], reveals_answer: support?.reveals_answer || false }); }
function support(value: Support) { action(async()=> { await expose(value); revealed.value.push(value.id); }); }
function play() { action(async()=> { await expose(); audio?.pause(); audio = new Audio(`/editorial/content-snapshots/${props.manifest.package_hash}/assets/${activity.value.content.asset_id}`); audio.onerror=()=>{ listeningFailed.value=true;message.value='Não foi possível reproduzir o áudio. Nenhuma falha linguística foi registrada.'; }; await audio.play(); audioReady.value=true; }); }
function stopPlayback() { audio?.pause(); }
function retryPending() { const event=events.value.find(e=>!e.received);if(event)action(async()=>{await send(event.kind,event.payload);message.value='Registro confirmado. Retome a atividade.';}); }
function submit(value: string | null, technical='valid') { action(async()=> {
  if(activity.value.modality==='listening' && listeningFailed.value)technical='failed';
  const supports = events.value.filter(e=>e.kind==='exposure' && e.payload.kind!=='training_use' && (e.payload.affected_modalities as string[]).includes(activity.value.modality)).map(e=>e.id);
  await send('attempt', { ...base(), modality: activity.value.modality, support_event_ids: supports, technical_status: technical, response: value, audio_reference: recordingUrl.value ? `local-only:${crypto.randomUUID()}` : null, assessment_status: activity.value.type==='information_question' ? 'pending' : 'not_requested' });
  recorded.value=true;
  feedback.value=technical!=='valid' ? 'Falha técnica registrada; nenhuma conclusão linguística.' : activity.value.type==='information_question' ? (activity.value.expected.accepted?.includes(value || '') ? 'Você identificou a informação nesta atividade. Retenção posterior não foi avaliada.' : 'Ouça novamente e identifique a escolha comunicada. Você pode continuar ou tentar outra vez.') : 'Prática registrada. A produção aberta continua sem avaliação independente.';
}); }
async function move() { stopRecording();stopPlayback();if(index.value>=props.mission.activities.length-1) {finished.value=true;return;} const next=props.mission.activities[index.value+1];if(next.type==='explanation')await send('exposure',{...base(),activity_id:next.id,target_ids:next.target_ids,support_id:`activity-${next.id}`,kind:'training_use',affected_modalities:[next.modality],reveals_answer:false});index.value++;reset(); }
function next() { action(move); }
function reset() { answer.value='';revealed.value=[];recorded.value=false;feedback.value='';audioReady.value=false; if(recordingUrl.value) URL.revokeObjectURL(recordingUrl.value);recordingUrl.value=null; }
function passive() { action(async()=> { await expose();await move(); }); }
async function startRecording() {
  audioError.value='';
  try {
    stream=await navigator.mediaDevices.getUserMedia({audio:true});
    if(disposed || document.hidden) { stream.getTracks().forEach(t=>t.stop());stream=null;return; }
    const chunks:BlobPart[]=[]; recorder=new MediaRecorder(stream);
    recorder.ondataavailable=e=>{if(e.data.size) chunks.push(e.data);};
    recorder.onstop=()=> { const blob=new Blob(chunks,{type:recorder?.mimeType || 'audio/webm'});if(recordingUrl.value) URL.revokeObjectURL(recordingUrl.value);if(!disposed)recordingUrl.value=URL.createObjectURL(blob);stream?.getTracks().forEach(t=>t.stop());stream=null;recording.value=false; };
    recorder.onerror=()=>{audioError.value='A gravação falhou. Tente novamente; sua capacidade não foi avaliada.';stopRecording();};
    recorder.start();recording.value=true;
  } catch { stream?.getTracks().forEach(t=>t.stop());stream=null;audioError.value='Microfone indisponível. Você pode continuar sem avaliação oral.'; }
}
function stopRecording() { if(recorder?.state==='recording') recorder.stop();stream?.getTracks().forEach(t=>t.stop());recording.value=false; }
function visibility() { if(document.hidden) {stopRecording();stopPlayback();} }
document.addEventListener('visibilitychange',visibility);
onBeforeUnmount(()=>{ disposed=true;document.removeEventListener('visibilitychange',visibility); stopRecording();stopPlayback(); if(recordingUrl.value)URL.revokeObjectURL(recordingUrl.value); });
</script>
<template><main><Link href="/editorial" class="preview-link">← Voltar à revisão</Link><p class="eyebrow">PRÉVIA EDITORIAL</p><h1>{{ mission.title }}</h1><aside>Material em rascunho, com voz sintética para revisão. Esta prévia não publica conteúdo nem confirma capacidades. Gravações ficam somente nesta página; a retomada persistente é feita no Android.</aside><article v-if="!finished"><p class="meta">Etapa {{ index+1 }} de {{ mission.activities.length }}</p><h2>{{ activity.prompt }}</h2><div v-if="activity.content.asset_id" class="actions"><button :disabled="busy" @click="play">{{ activity.type==='dialogue' ? 'Ouvir diálogo' : 'Ouvir atendente' }}</button><button v-if="audioReady" class="secondary" @click="stopPlayback">Pausar</button></div><p v-if="activity.content.pt_BR_note">{{ activity.content.pt_BR_note }}</p><p v-for="example in activity.content.examples" :key="example">{{ example }}</p><section v-for="item in activity.supports" :key="item.id"><p v-if="revealed.includes(item.id)">{{ item.content }}</p><button v-else class="secondary" :disabled="busy" @click="support(item)">{{ item.kind==='transcript' ? 'Ver transcrição' : 'Consultar apoio' }}</button></section><div v-if="activity.type==='information_question' && !recorded" class="actions"><button v-for="option in activity.content.options" :key="option" class="secondary" :disabled="busy" @click="submit(option)">{{ option }}</button></div><form v-if="activity.type==='written_recall' && !recorded" @submit.prevent="submit(answer)"><label>Sua resposta<textarea v-model="answer" rows="3" maxlength="10000" required /></label><button :disabled="busy || !answer.trim()">Registrar resposta</button></form><section v-if="['recorded_speech','communicative_task'].includes(activity.type) && !recorded"><p>O áudio da sua voz não será enviado.</p><button v-if="!recording" class="secondary" @click="startRecording">Gravar minha voz</button><button v-else @click="stopRecording">Parar gravação</button><p v-if="recording">Gravando…</p><p v-if="audioError" role="alert">{{ audioError }}</p><audio v-if="recordingUrl" :src="recordingUrl" controls /><div class="actions"><button :disabled="!recordingUrl || busy || recording" @click="submit(null)">Registrar prática oral</button><button class="secondary" :disabled="recording || busy" @click="next">Agora não posso falar · continuar</button></div></section><p v-if="feedback" role="status">{{ feedback }}</p><div v-if="recorded" class="actions"><button class="secondary" @click="recorded=false;feedback='';answer=''">Nova tentativa</button><button @click="next">Continuar</button></div><button v-if="['purpose','dialogue','explanation'].includes(activity.type)" :disabled="busy" @click="passive">Continuar</button><section v-if="activity.type==='summary'"><p>{{ events.filter(e=>e.kind==='attempt').length }} tentativas e {{ events.filter(e=>e.kind==='exposure').length }} exposições ou apoios registrados nesta prévia.</p><p>Produções abertas não foram avaliadas. Retenção posterior não foi verificada.</p><button @click="finished=true">Encerrar sessão</button></section><p v-if="message" role="alert">{{ message }}</p><button v-if="events.some(e=>!e.received)" class="secondary" :disabled="busy" @click="retryPending">Reenviar registro pendente</button></article><article v-else><h2>Sessão encerrada</h2><p>Você pode revisar o conteúdo ou retomar o painel. A prévia não alterou evidências de capacidade.</p><Link href="/editorial" class="preview-link">Voltar ao painel</Link></article></main></template>
