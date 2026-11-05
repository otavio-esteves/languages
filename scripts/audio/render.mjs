import { readFileSync, writeFileSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { join } from 'node:path';
const directory = 'content/pt-BR_en/missions';
const parts = [
  ['en-us', "I'd like a small coffee, please."],
  ['en-gb', 'For here or to go?'],
  ['en-us', 'Could you repeat that, please?'],
  ['en-gb', 'For here or to go?'],
  ['en-us', 'To go, please.'],
];
const temporary = mkdtempSync('/tmp/idiomas-audio-');
function render(id, utterances) {
  const buffers = utterances.map(([voice, text], index) => {
    const path = join(temporary, `${id}-${index}.wav`);
    execFileSync('espeak-ng', ['-v', voice, '-s', '135', '-w', path, text]);
    return readFileSync(path);
  });
  const audio = [];
  let header;
  for (const file of buffers) {
    if (file.toString('ascii',0,4) !== 'RIFF' || file.toString('ascii',36,40) !== 'data') throw new Error('Formato WAV inesperado');
    if (!header) header = Buffer.from(file.subarray(0,44));
    if (file.readUInt32LE(24) !== header.readUInt32LE(24)) throw new Error('Taxas incompatíveis');
    audio.push(file.subarray(44), Buffer.alloc(Math.floor(header.readUInt32LE(28)*0.45/header.readUInt16LE(32))*header.readUInt16LE(32)));
  }
  const body = Buffer.concat(audio); header.writeUInt32LE(body.length+36,4); header.writeUInt32LE(body.length,40);
  const result = Buffer.concat([header,body]); const hash = createHash('sha256').update(result).digest('hex');
  mkdirSync(join(directory,'assets'), {recursive:true});
  const path = `assets/${id}-${hash.slice(0,16)}.wav`; writeFileSync(join(directory,path),result);
  return { id, kind:'audio', status:'ready', script:utterances.map(([,t])=>t).join(' '), path, sha256:hash };
}
try {
  const dialogue = render('dialogue-audio',parts);
  const followup = render('followup-audio',[['en-gb','For here or to go?']]);
  for (const name of ['order-a-drink.v1.json','order-a-drink.evaluation-example.v1.json']) {
    const path = join(directory,name); const doc=JSON.parse(readFileSync(path,'utf8'));
    doc.assets=[dialogue,followup];
    writeFileSync(path,JSON.stringify(doc,null,2)+'\n');
  }
  console.log('Áudios sintéticos de prévia gerados com eSpeak NG; conteúdo permanece em rascunho.');
} finally { rmSync(temporary,{recursive:true,force:true}); }
