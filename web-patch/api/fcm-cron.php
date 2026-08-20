<?php
/**
 * Cron: process abandoned search notifications (every 10–15 min)
 * Example crontab:
 * */15 * * * * curl -s "https://www.ebostay.com/api/fcm-cron.php?key=YOUR_SECRET" >/dev/null
 */
require_once __DIR__ . '/../includes/functions.php';
require_once __DIR__ . '/../includes/fcm_push.php';

$key = $_GET['key'] ?? '';
$secret = defined('FCM_CRON_SECRET') ? FCM_CRON_SECRET : (getenv('FCM_CRON_SECRET') ?: 'ebo_fcm_cron_change_me');
if (!hash_equals((string)$secret, (string)$key)) {
    http_response_code(403);
    echo 'Forbidden';
    exit;
}

$pdo = getDB();
$n = fcmProcessAbandonedSearches($pdo, 80);
header('Content-Type: application/json');
echo json_encode(['success' => true, 'notified' => $n]);
