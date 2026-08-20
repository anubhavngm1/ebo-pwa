<?php
/**
 * Admin — send FCM push (all devices or one token)
 * POST JSON: { title, body, image?, url?, token? }
 */
header('Content-Type: application/json; charset=utf-8');
require_once __DIR__ . '/../../includes/functions.php';
require_once __DIR__ . '/../../includes/fcm_push.php';

if (session_status() === PHP_SESSION_NONE) session_start();
if (empty($_SESSION['admin_id'])) {
    http_response_code(401);
    echo json_encode(['success' => false, 'error' => 'Admin login required']);
    exit;
}

$raw = file_get_contents('php://input');
$body = $raw ? (json_decode($raw, true) ?: []) : $_POST;
$title = trim($body['title'] ?? '');
$text  = trim($body['body'] ?? $body['message'] ?? '');
$image = trim($body['image'] ?? $body['imageUrl'] ?? '');
$url   = trim($body['url'] ?? 'https://www.ebostay.com/pwa/');
$token = trim($body['token'] ?? '');

if ($title === '' || $text === '') {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'title and body required']);
    exit;
}

$pdo = getDB();
fcmEnsureTables($pdo);
$extra = ['url' => $url];
if ($image !== '') $extra['image'] = $image;

if ($token !== '') {
    $res = fcmSend($token, $title, $text, $extra);
} else {
    $res = fcmSendToAll($pdo, $title, $text, $extra);
}

$pdo->prepare("INSERT INTO fcm_send_log (title, body, image_url, target, sent_count, admin_id)
    VALUES (?,?,?,?,?,?)")->execute([
    $title, $text, $image ?: null, $token ? 'token' : 'all',
    (int)($res['sent'] ?? 0), (int)$_SESSION['admin_id']
]);

echo json_encode(array_merge(['success' => !empty($res['success'])], $res));
