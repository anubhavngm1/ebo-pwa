<?php
/**
 * Copy to fcm_config.php on the server (do not commit real keys).
 * Get Server key: Firebase Console → Project settings → Cloud Messaging
 * → Cloud Messaging API (Legacy) → Server key
 * If Legacy is disabled, enable "Cloud Messaging API" in Google Cloud Console
 * and create a server key, or migrate to HTTP v1 later.
 */
define('FCM_SERVER_KEY', 'PASTE_YOUR_FCM_SERVER_KEY_HERE');
define('FCM_CRON_SECRET', 'change_this_random_string');
