<?php
/**
 * Native app FCM token + search tracking
 * /pwa/api/fcm.php?action=register|track-search
 */
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-Customer-Id');
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(200); exit; }

require_once __DIR__ . '/../../includes/functions.php';
require_once __DIR__ . '/../../includes/fcm_push.php';

if (session_status() === PHP_SESSION_NONE) session_start();
$pdo = getDB();
fcmEnsureTables($pdo);

$action = $_REQUEST['action'] ?? '';
$body = [];
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $raw = file_get_contents('php://input');
    $body = $raw ? (json_decode($raw, true) ?: []) : [];
    $body = array_merge($_POST, $body);
}

function ok($d = []) { echo json_encode(array_merge(['success' => true], $d)); exit; }
function err($m) { http_response_code(400); echo json_encode(['success' => false, 'error' => $m]); exit; }

$cid = isset($_SESSION['customer_id']) ? (int)$_SESSION['customer_id'] : null;
if (!$cid && !empty($_SERVER['HTTP_X_CUSTOMER_ID']) && ctype_digit((string)$_SERVER['HTTP_X_CUSTOMER_ID'])) {
    $cid = (int)$_SERVER['HTTP_X_CUSTOMER_ID'];
}

switch ($action) {
    case 'register':
        $token = trim($body['token'] ?? $body['fcm_token'] ?? '');
        if ($token === '') err('token required');
        fcmRegisterToken($pdo, $token, $cid, $body['platform'] ?? 'android');
        ok(['registered' => true]);

    case 'track-search':
        $q = trim($body['query'] ?? $body['q'] ?? '');
        $token = trim($body['token'] ?? $body['fcm_token'] ?? '');
        if ($q === '') err('query required');
        fcmTrackSearch($pdo, $q, $token ?: null, $cid);
        ok(['tracked' => true]);

    default:
        err('Unknown action');
}
