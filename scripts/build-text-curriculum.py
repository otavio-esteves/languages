#!/usr/bin/env python3
"""Build the versioned training draft from its editable, bilingual day plan.

Source format: # title | teaching note, then three > lesson headings, each
followed by three English || Portuguese [|| alternative English ...] rows.
Only the first two rows appear in the model. The third is a new writing task.
Run with --check to detect drift without modifying the generated package.
"""

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "content/pt-BR_en/curriculum/days.v5.txt"
OUTPUT = ROOT / "content/pt-BR_en/missions/order-a-drink.v5.json"


def read_days():
    days = []
    for number, raw in enumerate(SOURCE.read_text().splitlines(), 1):
        line = raw.strip()
        if not line:
            continue
        if line.startswith("# "):
            title, note = line[2:].split(" | ", 1)
            days.append({"title": title, "note": note, "lessons": []})
        elif line.startswith("> "):
            days[-1]["lessons"].append({"title": line[2:], "items": []})
        else:
            columns = line.split(" || ")
            if len(columns) < 2 or not all(columns):
                raise ValueError(f"Linha bilíngue inválida: {number}")
            english, portuguese, *variants = columns
            days[-1]["lessons"][-1]["items"].append({
                "en": english, "pt": portuguese, "accepted": [english, *variants],
            })
    assert len(days) == 53, "A trilha deve conter os três dias iniciais mais 50 novos."
    for day, data in enumerate(days, 1):
        assert len(data["lessons"]) == 3, f"Dia {day}: três lições esperadas."
        for lesson, group in enumerate(data["lessons"], 1):
            assert len(group["items"]) == 3, f"Dia {day}, lição {lesson}: três mensagens esperadas."
            for index, item in enumerate(group["items"], 1):
                item["id"] = f"d{day:02}-l{lesson}-t{index}"
    return days


def build(days):
    package = {
        "schema_version": "1.0.0", "id": "order-a-drink", "version": 5,
        "minimum_reader_version": "1.0.0", "status": "draft", "purpose": "training",
        "title": "Inglês no dia a dia · 53 dias", "language_pair": "pt-BR_en",
        "capability_ids": ["everyday-reading", "everyday-writing"], "prerequisite_ids": [],
        "targets": [],
        "provenance": {"author_type": "ai_assisted_draft", "license": "pending_owner_decision", "reviewer": None, "reviewed_at": None},
        "assets": [],
        "rubric": {"id": "everyday-text", "version": 1, "dimensions": [
            {"id": "meaning", "essential": True, "criterion": "Comunica as informações solicitadas na situação escrita."},
            {"id": "reading", "essential": True, "criterion": "Identifica o sentido das mensagens neste contexto; não promove outras modalidades."},
            {"id": "form", "essential": False, "criterion": "Compara variantes previstas; formas não previstas exigem abstenção, sem inferir domínio ou retenção."},
        ]},
        "activities": [],
    }
    # Lexical hints are always declared as support in the current modality.
    lexicon = json.loads((SOURCE.parent / "glossary.v5.json").read_text())
    known_errors = json.loads((SOURCE.parent / "known-errors.v5.json").read_text())
    for day_index, day in enumerate(days):
        day_id = f"day-{day_index + 1:02}"
        level = "A1" if day_index < 40 else "A2"
        for lesson_index, lesson in enumerate(day["lessons"]):
            items = lesson["items"]
            for item in items:
                package["targets"].append({"id": item["id"], "expression": item["en"], "meaning": item["pt"], "rationale": f"{day['title']}: {lesson['title']}."})
            prefix = f"{day_id}-lesson-{lesson_index + 1}"
            model = "\n".join(item["en"] for item in items[:2])

            def add(suffix, kind, prompt, targets, seconds, content=None, expected=None):
                modality = "writing" if kind in ("written_recall", "communicative_task") else "reading"
                body = {
                    "phase_id": day_id, "phase_title": day["title"], "phase_description": day["note"],
                    "phase_level": level, "day_estimated_minutes": {"min": 10, "max": 15},
                    "estimated_seconds": seconds, "label": lesson["title"], **(content or {}),
                }
                supports = []
                if expected and "comparison" in expected:
                    supports.append({"id": "comparison-example", "kind": "solution", "content": expected["comparison"]["example"], "affected_modalities": [modality], "reveals_answer": True})
                # Keep the offline package within the existing 2 MiB reader contract.
                # Hints cover selected words, rather than displaying whole solutions.
                visible = " ".join([body.get("study_text", ""), *[item["en"] for item in targets]])
                words = set(re.findall(r"[\w'-]+", visible.lower()))
                selected = [(word, meaning) for word, meaning in lexicon.items() if word in words][:3]
                if selected:
                    body["glossary"] = {}
                    for word, meaning in selected:
                        support_id = f"gloss-{word}"
                        body["glossary"][word] = {"meaning": meaning, "support_id": support_id}
                        supports.append({"id": support_id, "kind": "hint", "content": meaning, "affected_modalities": [modality], "reveals_answer": True})
                package["activities"].append({
                    "id": f"{prefix}-{suffix}", "type": kind, "modality": modality,
                    "prompt": prompt, "target_ids": [item["id"] for item in targets],
                    "supports": supports, "content": body, "expected": expected or {"evidence": "exposure_only"},
                    "feedback": "Confira o significado e os detalhes solicitados. Você pode consultar apoio e tentar novamente; isso fica registrado.",
                })

            def write(suffix, item, kind, seconds, prompt):
                add(suffix, kind, prompt, [item], seconds, expected={"comparison": {
                    "version": "text-variants-v1", "accepted": item["accepted"],
                    "corrections": known_errors.get(item["en"], []), "example": item["en"],
                }})
                if kind == "communicative_task":
                    words = set(re.findall(r"[\w'-]+", item["en"].lower()))
                    hints = [f"{word}: {meaning}" for word, meaning in lexicon.items() if word in words][:6]
                    if hints:
                        package["activities"][-1]["supports"].append({
                            "id": "task-vocabulary", "kind": "hint",
                            "content": "Palavras para ajudar: " + "; ".join(hints),
                            "affected_modalities": ["writing"], "reveals_answer": True,
                        })

            add("read", "explanation", f"{lesson['title']}: leia e observe o sentido.", items[:2], 40,
                {"study_text": model, "pt_BR_note": day["note"]})
            for index, item in enumerate(items[:2]):
                choices = [other["pt"] for other in items]
                # Rotate the answer positions; correct is not always the first option.
                offset = (day_index + lesson_index + index) % len(choices)
                choices = choices[offset:] + choices[:offset]
                add(f"meaning-{index + 1}", "information_question", f"Na leitura, o que comunica “{item['en']}”?", [item], 20,
                    {"study_text": model, "options": choices, "options_language": "pt-BR"}, {"accepted": [item["pt"]]})
            for index, item in enumerate(items[:2]):
                write(f"recall-{index + 1}", item, "written_recall", 40, f"Sem o modelo na tela, escreva em inglês: {item['pt']}")
            # Explicit editorial revisits, not a calendar or an adaptive F3 scheduler.
            review_day = max(0, day_index - (1, 3, 7)[lesson_index])
            review = days[review_day]["lessons"][lesson_index]["items"][0]
            write("revisit", review, "written_recall", 40, f"Retome o Dia {review_day + 1}, {days[review_day]['title']}. Escreva em inglês: {review['pt']}")
            write("use", items[2], "communicative_task", 50,
                  f"Uma nova mensagem na situação «{day['title']}»: comunique em inglês “{items[2]['pt']}”. Você pode usar outra formulação.")
            add("close", "summary", f"{lesson['title']}: encerre a lição.", items, 10,
                {"pt_BR_note": "Você leu, escreveu e retomou uma expressão. Se algum detalhe ficou difícil, pode praticar novamente ou consultar apoio. O percurso não confirma retenção."})
    return package


def render(package):
    # One activity per line keeps diffs reviewable without exceeding the mobile limit.
    metadata = {key: value for key, value in package.items() if key != "activities"}
    compact = lambda value: json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    return (compact(metadata)[:-1] + ',"activities":[\n' + ",\n".join(compact(a) for a in package["activities"]) + "\n]}\n").encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    package = build(read_days())
    result = render(package)
    if len(result) > 2 * 1024 * 1024:
        raise SystemExit(f"Pacote excede o limite offline de 2 MiB: {len(result)} bytes.")
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_bytes() != result:
            raise SystemExit("Pacote difere do plano. Execute scripts/build-text-curriculum.py e revise o diff.")
    else:
        OUTPUT.write_bytes(result)
    print(f"53 dias · {len(package['activities'])} atividades · {len(result)} bytes · 13 min/dia planejados")


if __name__ == "__main__":
    main()
