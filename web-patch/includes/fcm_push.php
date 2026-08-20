<?php
/**
 * EBO Stay — Firebase Cloud Messaging (legacy HTTP)
 * Requires FCM_SERVER_KEY in config or env.
 *
 * Firebase Console → Project settings → Cloud Messaging →
 * Cloud Messaging API (Legacy) → Server key
 * Or enable API and use a server key from Google Cloud.
 */
if (!function_exists('fcmEnsureTables')) {
function fcmEnsureTables(PDO $pdo): void {
    static $done = false;
    if ($done) return;
    $done = true;
    $pdo->exec("CREATE TABLE IF NOT EXISTS fcm_devices (
        id INT AUTO_INCREMENT PRIMARY KEY,
        token VARCHAR(255) NOT NULL UNIQUE,
        customer_id INT DEFAULT NULL,
        platform VARCHAR(20) DEFAULT 'android',
        last_seen_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_customer (customer_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS fcm_search_events (
        id INT AUTO_INCREMENT PRIMARY KEY,
        token VARCHAR(255) DEFAULT NULL,
        customer_id INT DEFAULT NULL,
        query_text VARCHAR(200) NOT NULL,
        location VARCHAR(120) DEFAULT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        notify_after DATETIME DEFAULT NULL,
        notified_at DATETIME DEFAULT NULL,
        INDEX idx_pending (notified_at, notify_after)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS fcm_send_log (
        id INT AUTO_INCREMENT PRIMARY KEY,
        title VARCHAR(200) NOT NULL,
        body TEXT,
        image_url VARCHAR(500) DEFAULT NULL,
        target VARCHAR(50) DEFAULT 'all',
        sent_count INT DEFAULT 0,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        admin_id INT DEFAULT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}
}

if (!function_exists('fcmGetServerKey')) {
function fcmGetServerKey(): string {
    if (defined('FCM_SERVER_KEY') && FCM_SERVER_KEY) return (string)FCM_SERVER_KEY;
    $env = getenv('FCM_SERVER_KEY');
    if ($env) return $env;
    // Optional local config file (not in git)
    $cfg = __DIR__ . '/fcm_config.php';
    if (is_file($cfg)) {
        include_once $cfg;
        if (defined('FCM_SERVER_KEY') && FCM_SERVER_KEY) return (string)FCM_SERVER_KEY;
    }
    return '';
}
}

if (!function_exists('fcmRegisterToken')) {
function fcmRegisterToken(PDO $pdo, string $token, ?int $customerId = null, string $platform = 'android'): void {
    fcmEnsureTables($pdo);
    $token = trim($token);
    if ($token === '' || strlen($token) < 20) return;
    $stmt = $pdo->prepare("INSERT INTO fcm_devices (token, customer_id, platform, last_seen_at)
        VALUES (?, ?, ?, NOW())
        ON DUPLICATE KEY UPDATE customer_id = COALESCE(VALUES(customer_id), customer_id),
          platform = VALUES(platform), last_seen_at = NOW()");
    $stmt->execute([$token, $customerId, $platform]);
}
}

if (!function_exists('fcmSend')) {
/**
 * @param string|array $to token string, array of tokens, or '/topics/all'
 * @return array{success:bool,sent:int,error?:string,raw?:mixed}
 */
function fcmSend($to, string $title, string $body, array $extra = []): array {
    $key = fcmGetServerKey();
    if ($key === '') {
        return ['success' => false, 'sent' => 0, 'error' => 'FCM_SERVER_KEY not configured'];
    }

    $image = $extra['image'] ?? $extra['imageUrl'] ?? null;
    $link  = $extra['url'] ?? $extra['link'] ?? 'https://www.ebostay.com/pwa/';

    $data = array_merge([
        'title' => $title,
        'body'  => $body,
        'url'   => $link,
    ], $extra);
    if ($image) $data['image'] = $image;

    $notification = [
        'title' => $title,
        'body'  => $body,
    ];
    if ($image) $notification['image'] = $image;

    $payload = [
        'priority' => 'high',
        'notification' => $notification,
        'data' => array_map('strval', $data),
    ];

    if (is_string($to) && str_starts_with($to, '/topics/')) {
        $payload['to'] = $to;
    } elseif (is_array($to)) {
        $tokens = array_values(array_filter(array_unique($to)));
        if (!$tokens) return ['success' => false, 'sent' => 0, 'error' => 'No tokens'];
        // FCM legacy multicast max 1000
        $sent = 0;
        $last = null;
        foreach (array_chunk($tokens, 500) as $chunk) {
            $payload['registration_ids'] = $chunk;
            unset($payload['to']);
            $last = fcmHttpPost($key, $payload);
            if (!empty($last['success'])) $sent += count($chunk);
        }
        return ['success' => $sent > 0, 'sent' => $sent, 'raw' => $last];
    } else {
        $payload['to'] = $to;
    }

    $res = fcmHttpPost($key, $payload);
    return [
        'success' => !empty($res['success']),
        'sent' => !empty($res['success']) ? 1 : 0,
        'error' => $res['error'] ?? null,
        'raw' => $res['raw'] ?? null,
    ];
}
}

if (!function_exists('fcmHttpPost')) {
function fcmHttpPost(string $key, array $payload): array {
    $ch = curl_init('https://fcm.googleapis.com/fcm/send');
    curl_setopt_array($ch, [
        CURLOPT_POST => true,
        CURLOPT_HTTPHEADER => [
            'Authorization: key=' . $key,
            'Content-Type: application/json',
        ],
        CURLOPT_POSTFIELDS => json_encode($payload),
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT => 20,
    ]);
    $raw = curl_exec($ch);
    $err = curl_error($ch);
    $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    if ($raw === false) return ['success' => false, 'error' => $err ?: 'curl failed'];
    $json = json_decode($raw, true);
    $ok = $code >= 200 && $code < 300 && (empty($json['failure']) || ($json['success'] ?? 0) > 0);
    return ['success' => $ok, 'error' => $ok ? null : ($json['results'][0]['error'] ?? $raw), 'raw' => $json];
}
}

if (!function_exists('fcmSendToAll')) {
function fcmSendToAll(PDO $pdo, string $title, string $body, array $extra = []): array {
    fcmEnsureTables($pdo);
    $tokens = $pdo->query("SELECT token FROM fcm_devices ORDER BY last_seen_at DESC LIMIT 2000")->fetchAll(PDO::FETCH_COLUMN);
    if (!$tokens) return ['success' => false, 'sent' => 0, 'error' => 'No registered devices'];
    return fcmSend($tokens, $title, $body, $extra);
}
}

if (!function_exists('fcmTrackSearch')) {
function fcmTrackSearch(PDO $pdo, string $query, ?string $token = null, ?int $customerId = null): void {
    fcmEnsureTables($pdo);
    $query = trim(mb_substr($query, 0, 200));
    if ($query === '') return;
    // crude location extract
    $loc = preg_replace('/\b(hotels?|packages?|in|at|near|for|the|a|an)\b/iu', ' ', $query);
    $loc = trim(preg_replace('/\s+/', ' ', $loc));
    $pdo->prepare("INSERT INTO fcm_search_events (token, customer_id, query_text, location, notify_after)
        VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL 1 HOUR))")
        ->execute([$token, $customerId, $query, $loc ?: null]);
}
}

if (!function_exists('fcmProcessAbandonedSearches')) {
function fcmProcessAbandonedSearches(PDO $pdo, int $limit = 50): int {
    fcmEnsureTables($pdo);
    $stmt = $pdo->query("SELECT * FROM fcm_search_events
        WHERE notified_at IS NULL AND notify_after IS NOT NULL AND notify_after <= NOW()
        ORDER BY id ASC LIMIT " . (int)$limit);
    $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);
    $n = 0;
    foreach ($rows as $row) {
        $loc = $row['location'] ?: $row['query_text'];
        $title = 'Still looking for ' . $loc . '?';
        $body = 'Enjoy exclusive deals on ' . $loc . ' — open EBO Stay & save today.';
        $token = $row['token'];
        if (!$token && $row['customer_id']) {
            $t = $pdo->prepare("SELECT token FROM fcm_devices WHERE customer_id = ? ORDER BY last_seen_at DESC LIMIT 1");
            $t->execute([(int)$row['customer_id']]);
            $token = $t->fetchColumn() ?: null;
        }
        if ($token) {
            $res = fcmSend($token, $title, $body, [
                'url' => 'https://www.ebostay.com/pwa/#hotels',
                'type' => 'abandoned_search',
            ]);
            if (!empty($res['success'])) $n++;
        }
        $pdo->prepare("UPDATE fcm_search_events SET notified_at = NOW() WHERE id = ?")->execute([(int)$row['id']]);
    }
    return $n;
}
}

if (!function_exists('fcmNotifyBookingConfirmed')) {
function fcmNotifyBookingConfirmed(PDO $pdo, ?int $customerId, string $ref, string $titleLabel = 'Booking confirmed'): void {
    if (!$customerId) return;
    fcmEnsureTables($pdo);
    $t = $pdo->prepare("SELECT token FROM fcm_devices WHERE customer_id = ? ORDER BY last_seen_at DESC LIMIT 5");
    $t->execute([$customerId]);
    $tokens = $t->fetchAll(PDO::FETCH_COLUMN);
    if (!$tokens) return;
    fcmSend($tokens, $titleLabel . ' ✓',
        'Your booking ' . $ref . ' is confirmed. Have a great trip!',
        ['url' => 'https://www.ebostay.com/pwa/#my-bookings', 'type' => 'booking_confirmed', 'ref' => $ref]);
}
}
