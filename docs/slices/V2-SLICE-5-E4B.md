# V2 slice 5: let the owner try Gemma 4 E4B (spec for the implementer)

Parent: `docs/BHEEM-AGENT-V2.md`. Read `AGENTS.md` first and obey it.

## Goal

The owner can switch the language model between Gemma 4 E2B (current default, 2.59 GB) and Gemma 4 E4B (larger, better
reasoning, 3.66 GB) from Settings, so we can measure E4B on the owner's phone (moto g86 power, 8 GB RAM, measured) and
then choose. E2B stays the default; nothing changes for anyone who does not switch.

## Changes

1. **`app/src/main/assets/models.json`**: add a second `llm` model, `"default"` absent or false:
   - id `gemma-4-e4b`, runtime `litert-lm`, license `Apache-2.0`, `minRamGb` 8, the same `profile` as `gemma-4-e2b`.
   - file `gemma-4-E4B-it.litertlm`, sha256 `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`,
     bytes `3659530240`, url
     `https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm`
     (values from the Hugging Face API file listing, 5 Nov 2026).
   Keep `gemma-4-e2b` marked default. Update `RegistryAndFilesTest` if it asserts the model count.
2. **Model choice stored in preferences** (key for example `llm_model_id`). `AppGraph` passes it as `preferredId` to
   `ModelSelection.pick`; an unknown or missing id falls back to the default (catch the exception, clear the bad value).
3. **Settings row "Language model"** showing the current model name, file size and a short note, with two choices:
   "Gemma 4 E2B (default, 2.6 GB)" and "Gemma 4 E4B (smarter, 3.7 GB, needs about 8 GB RAM)". Choosing one stores the id
   and tells the owner that the app restarts to load it and that the first start downloads the model through the
   existing model download screen and its existing consent. Use the existing restart mechanism if there is one;
   otherwise ask the owner to close and reopen the app. Strings in resources.
4. **Per-model files.** The download and verification code must keep each model's file separately so switching back to
   E2B does not download it again. Do not delete any model file automatically. Add a "Remove the other model" action in
   the same Settings row that deletes only the file of the model that is not selected, after a confirmation dialog.
5. **Timing log.** The existing `BheemChat` log line ("first text ... ms, answer ... ms") must include the model id, so
   the trial can be read from logcat.
6. **Docs.** In `docs/MODELS.md` add a short "Gemma 4 E4B trial" entry: file, size and sha256 (from the Hugging Face API
   listing, 5 Nov 2026), license, and "on-device numbers: not yet measured".

## Tests (write first)

Registry parses both models and still picks E2B by default; picking `gemma-4-e4b` by id works; an unknown stored id falls
back to the default; the "other model" file path is the right one for each selection.

## Security

No new action, no new network path: downloads keep going only through the existing downloader in the `full` flavor and
its consent screen. `scripts/check-network-use.sh` must still pass.

## Done when

`bash .oc/ci.sh tests`, `bash .oc/ci.sh gate`, `bash scripts/check-ui-strings.sh` and `bash scripts/check-network-use.sh`
pass, and the change is committed on branch `v2/slice-5`.
