/* NewbiesDev UI config overrides — template.
 *
 * Copy this file to `config-overrides.js` (gitignored — never commit real
 * values) for local development. CI injects a generated copy from the
 * NBT_DEV_RELAY_CONFIG repository secret at release time, so the submission
 * relay channel and similar capability values never live in the repo.
 *
 * Loaded by index.html BEFORE app.js; app.js reads window.NBT_CONFIG_OVERRIDES.
 */
window.NBT_CONFIG_OVERRIDES = {
  // ntfy (or any HTTP-POST-able inbox) topic used for team submissions.
  submissionChannel: "REPLACE_WITH_YOUR_PRIVATE_CHANNEL",
  relayUrl: "https://ntfy.sh/REPLACE_WITH_YOUR_PRIVATE_CHANNEL",
  relayFallbackUrl: "https://ntfy.sh/REPLACE_WITH_YOUR_PRIVATE_CHANNEL_B"
};
