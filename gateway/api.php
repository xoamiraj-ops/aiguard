<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

$configFile = __DIR__ . '/config.local.php';
if (is_file($configFile)) {
    $config = require $configFile;
} else {
    $config = [
        'espo_url' => getenv('ESPO_URL') ?: 'https://crm.amniathoshmand.ir',
        'espo_api_key' => getenv('ESPO_API_KEY') ?: '',
        'openai_api_key' => getenv('OPENAI_API_KEY') ?: '',
        'device_token' => getenv('PARSBAN_DEVICE_TOKEN') ?: '',
        'transcribe_model' => getenv('TRANSCRIBE_MODEL') ?: 'gpt-4o-transcribe-diarize',
        'summary_model' => getenv('SUMMARY_MODEL') ?: 'gpt-6-luna',
        'crm_attachment_max_mb' => (int) (getenv('CRM_ATTACHMENT_MAX_MB') ?: 25),
    ];
}

function out(array $payload, int $status = 200): never {
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function require_config(array $config): void {
    foreach (['espo_url', 'espo_api_key', 'openai_api_key', 'device_token'] as $key) {
        if (empty($config[$key])) {
            out(['ok' => false, 'error' => "Gateway config missing: $key"], 503);
        }
    }
}

function auth(array $config): void {
    $expected = (string) ($config['device_token'] ?? '');
    $actual = (string) ($_SERVER['HTTP_X_PARSBAN_TOKEN'] ?? '');
    if ($expected === '' || $actual === '' || !hash_equals($expected, $actual)) {
        out(['ok' => false, 'error' => 'Unauthorized'], 401);
    }
}

function json_input(): array {
    $raw = file_get_contents('php://input');
    $data = json_decode($raw ?: '{}', true);
    return is_array($data) ? $data : [];
}

function phone_digits(string $value): string {
    return preg_replace('/\D+/', '', $value) ?? '';
}

function phone_variants(string $phone): array {
    $d = phone_digits($phone);
    $variants = [$phone, $d];

    if (str_starts_with($d, '0098')) {
        $local = '0' . substr($d, 4);
        $variants[] = $local;
        $variants[] = '+98' . substr($d, 4);
    } elseif (str_starts_with($d, '98')) {
        $local = '0' . substr($d, 2);
        $variants[] = $local;
        $variants[] = '+98' . substr($d, 2);
    } elseif (str_starts_with($d, '0')) {
        $variants[] = '+98' . substr($d, 1);
        $variants[] = '98' . substr($d, 1);
    }

    return array_values(array_unique(array_filter($variants)));
}

function espo_request(array $config, string $method, string $path, ?array $payload = null, array $query = []): array {
    $base = rtrim((string) $config['espo_url'], '/') . '/api/v1/';
    $url = $base . ltrim($path, '/');
    if ($query) {
        $url .= '?' . http_build_query($query);
    }

    $ch = curl_init($url);
    $headers = [
        'Accept: application/json',
        'Content-Type: application/json',
        'X-Api-Key: ' . $config['espo_api_key'],
    ];

    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_CONNECTTIMEOUT => 15,
        CURLOPT_TIMEOUT => 90,
    ]);

    if ($payload !== null) {
        curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode(
            $payload,
            JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
        ));
    }

    $raw = curl_exec($ch);
    $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err = curl_error($ch);
    curl_close($ch);

    if ($raw === false || $err !== '') {
        throw new RuntimeException('EspoCRM connection failed: ' . $err);
    }
    if ($status < 200 || $status >= 300) {
        throw new RuntimeException("EspoCRM HTTP $status: $raw");
    }

    $decoded = json_decode($raw, true);
    if (is_array($decoded)) return $decoded;

    return ['raw' => $raw];
}

function crm_lookup(array $config, string $phone): ?array {
    foreach (phone_variants($phone) as $variant) {
        $where = [[
            'type' => 'equals',
            'attribute' => 'phoneNumber',
            'value' => $variant,
        ]];

        $params = [
            'maxSize' => 1,
            'select' => 'id,name,phoneNumber',
            'whereGroup' => json_encode($where, JSON_UNESCAPED_UNICODE),
        ];

        $result = espo_request($config, 'GET', 'Account', null, $params);
        if (!empty($result['list'][0]['id'])) {
            $row = $result['list'][0];
            return [
                'type' => 'Account',
                'id' => (string) $row['id'],
                'name' => (string) ($row['name'] ?? $variant),
            ];
        }

        $params['select'] = 'id,name,phoneNumber,accountId,accountName';
        $result = espo_request($config, 'GET', 'Contact', null, $params);
        if (!empty($result['list'][0]['id'])) {
            $row = $result['list'][0];

            if (!empty($row['accountId'])) {
                return [
                    'type' => 'Account',
                    'id' => (string) $row['accountId'],
                    'name' => (string) ($row['accountName'] ?? $row['name'] ?? $variant),
                    'contactId' => (string) $row['id'],
                    'contactName' => (string) ($row['name'] ?? ''),
                ];
            }

            return [
                'type' => 'Contact',
                'id' => (string) $row['id'],
                'name' => (string) ($row['name'] ?? $variant),
            ];
        }
    }

    return null;
}

function openai_transcribe(array $config, string $path, string $name, string $mime): array {
    $ch = curl_init('https://api.openai.com/v1/audio/transcriptions');
    $fields = [
        'file' => new CURLFile($path, $mime ?: 'application/octet-stream', $name),
        'model' => (string) $config['transcribe_model'],
        'language' => 'fa',
        'response_format' => 'diarized_json',
        'chunking_strategy' => 'auto',
    ];

    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_POST => true,
        CURLOPT_HTTPHEADER => [
            'Authorization: Bearer ' . $config['openai_api_key'],
        ],
        CURLOPT_POSTFIELDS => $fields,
        CURLOPT_CONNECTTIMEOUT => 20,
        CURLOPT_TIMEOUT => 600,
    ]);

    $raw = curl_exec($ch);
    $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err = curl_error($ch);
    curl_close($ch);

    if ($raw === false || $err !== '') {
        throw new RuntimeException('Transcription connection failed: ' . $err);
    }
    if ($status < 200 || $status >= 300) {
        throw new RuntimeException("Transcription HTTP $status: $raw");
    }

    $json = json_decode($raw, true);
    if (!is_array($json)) {
        throw new RuntimeException('Invalid transcription response');
    }

    $lines = [];
    if (!empty($json['segments']) && is_array($json['segments'])) {
        foreach ($json['segments'] as $segment) {
            $speaker = (string) ($segment['speaker'] ?? '?');
            $start = (float) ($segment['start'] ?? 0);
            $text = trim((string) ($segment['text'] ?? ''));
            if ($text === '') continue;
            $minutes = floor($start / 60);
            $seconds = floor($start) % 60;
            $lines[] = sprintf('[%s %02d:%02d] %s', $speaker, $minutes, $seconds, $text);
        }
    }

    $transcript = $lines
        ? implode("\n", $lines)
        : trim((string) ($json['text'] ?? ''));

    return [
        'text' => $transcript,
        'raw' => $json,
    ];
}

function response_output_text(array $response): string {
    foreach (($response['output'] ?? []) as $item) {
        foreach (($item['content'] ?? []) as $content) {
            if (($content['type'] ?? '') === 'output_text' && isset($content['text'])) {
                return (string) $content['text'];
            }
        }
    }
    return '';
}

function openai_summarize(array $config, string $transcript, array $meta): array {
    $schema = [
        'type' => 'object',
        'properties' => [
            'title' => ['type' => 'string'],
            'summary' => ['type' => 'string'],
            'customer_request' => ['type' => 'string'],
            'commitments' => ['type' => 'string'],
            'sentiment' => [
                'type' => 'string',
                'enum' => ['positive', 'neutral', 'negative', 'mixed'],
            ],
            'follow_up_needed' => ['type' => 'boolean'],
            'follow_up_title' => ['type' => 'string'],
            'follow_up_at' => ['type' => ['string', 'null']],
        ],
        'required' => [
            'title', 'summary', 'customer_request', 'commitments',
            'sentiment', 'follow_up_needed', 'follow_up_title', 'follow_up_at'
        ],
        'additionalProperties' => false,
    ];

    $input = "این متن پیاده‌سازی یک مکالمه تلفنی کسب‌وکار دوربین مداربسته و خدمات فنی است.\n"
        . "خلاصه را فارسی، دقیق و بدون حدس بنویس. اگر موعد پیگیری صریح نیست follow_up_at را null بگذار. "
        . "اگر گوینده‌ها A/B هستند نقش مشتری و کارشناس را فقط وقتی از متن قابل استنباط است در خلاصه بیان کن.\n\n"
        . "شماره: " . ($meta['phone'] ?? '') . "\n"
        . "جهت تماس: " . ($meta['direction'] ?? '') . "\n\n"
        . $transcript;

    $payload = [
        'model' => (string) $config['summary_model'],
        'input' => $input,
        'text' => [
            'format' => [
                'type' => 'json_schema',
                'name' => 'call_summary',
                'strict' => true,
                'schema' => $schema,
            ],
        ],
    ];

    $ch = curl_init('https://api.openai.com/v1/responses');
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_POST => true,
        CURLOPT_HTTPHEADER => [
            'Authorization: Bearer ' . $config['openai_api_key'],
            'Content-Type: application/json',
        ],
        CURLOPT_POSTFIELDS => json_encode($payload, JSON_UNESCAPED_UNICODE),
        CURLOPT_CONNECTTIMEOUT => 20,
        CURLOPT_TIMEOUT => 180,
    ]);

    $raw = curl_exec($ch);
    $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err = curl_error($ch);
    curl_close($ch);

    if ($raw === false || $err !== '') {
        throw new RuntimeException('Summary connection failed: ' . $err);
    }
    if ($status < 200 || $status >= 300) {
        throw new RuntimeException("Summary HTTP $status: $raw");
    }

    $response = json_decode($raw, true);
    if (!is_array($response)) throw new RuntimeException('Invalid summary response');

    $text = response_output_text($response);
    $summary = json_decode($text, true);
    if (!is_array($summary)) {
        throw new RuntimeException('Structured summary could not be parsed');
    }
    return $summary;
}

function format_summary(array $s): string {
    $parts = [];
    if (!empty($s['summary'])) $parts[] = "خلاصه:\n" . $s['summary'];
    if (!empty($s['customer_request'])) $parts[] = "درخواست مشتری:\n" . $s['customer_request'];
    if (!empty($s['commitments'])) $parts[] = "تعهدات/قول‌ها:\n" . $s['commitments'];
    if (!empty($s['sentiment'])) $parts[] = "وضعیت مکالمه: " . $s['sentiment'];
    return implode("\n\n", $parts);
}

function crm_save_call(
    array $config,
    array $match,
    array $meta,
    array $summary,
    string $transcript,
    string $audioPath,
    string $audioName,
    string $audioMime
): array {
    $startMs = (int) ($meta['connectedAt'] ?: $meta['startedAt'] ?: 0);
    $endMs = (int) ($meta['endedAt'] ?: 0);
    if ($startMs <= 0) $startMs = (int) (microtime(true) * 1000);
    if ($endMs <= $startMs) $endMs = $startMs + 60000;

    $callPayload = [
        'name' => (string) ($summary['title'] ?? 'تماس تلفنی'),
        'status' => 'Held',
        'direction' => (($meta['direction'] ?? '') === 'Outgoing') ? 'Outbound' : 'Inbound',
        'dateStart' => gmdate('Y-m-d H:i:s', (int) floor($startMs / 1000)),
        'dateEnd' => gmdate('Y-m-d H:i:s', (int) floor($endMs / 1000)),
        'description' => format_summary($summary),
    ];

    if (!empty($match['type']) && !empty($match['id'])) {
        $callPayload['parentType'] = $match['type'];
        $callPayload['parentId'] = $match['id'];
    }

    $call = espo_request($config, 'POST', 'Call', $callPayload);
    $callId = (string) ($call['id'] ?? '');
    if ($callId === '') throw new RuntimeException('EspoCRM Call ID missing');

    $attachmentId = '';
    $maxBytes = max(1, (int) $config['crm_attachment_max_mb']) * 1024 * 1024;

    if (is_file($audioPath) && filesize($audioPath) <= $maxBytes) {
        $dataUri = 'data:' . ($audioMime ?: 'application/octet-stream')
            . ';base64,' . base64_encode((string) file_get_contents($audioPath));

        $attachment = espo_request($config, 'POST', 'Attachment', [
            'name' => $audioName,
            'type' => $audioMime ?: 'application/octet-stream',
            'role' => 'Attachment',
            'parentType' => 'Note',
            'field' => 'attachments',
            'file' => $dataUri,
        ]);

        $attachmentId = (string) ($attachment['id'] ?? '');
    }

    $notePayload = [
        'type' => 'Post',
        'parentType' => 'Call',
        'parentId' => $callId,
        'post' => "رونوشت کامل مکالمه:\n\n" . $transcript,
    ];
    if ($attachmentId !== '') {
        $notePayload['attachmentsIds'] = [$attachmentId];
    }
    espo_request($config, 'POST', 'Note', $notePayload);

    $taskId = '';
    if (!empty($summary['follow_up_needed'])) {
        $taskPayload = [
            'name' => trim((string) ($summary['follow_up_title'] ?? 'پیگیری تماس')),
            'status' => 'Not Started',
            'priority' => 'Normal',
            'description' => "پیگیری ایجادشده خودکار از تماس: " . ($summary['summary'] ?? ''),
        ];

        if (!empty($match['type']) && !empty($match['id'])) {
            $taskPayload['parentType'] = $match['type'];
            $taskPayload['parentId'] = $match['id'];
        } else {
            $taskPayload['parentType'] = 'Call';
            $taskPayload['parentId'] = $callId;
        }

        $followAt = $summary['follow_up_at'] ?? null;
        if (is_string($followAt) && trim($followAt) !== '') {
            $ts = strtotime($followAt);
            if ($ts !== false) {
                $taskPayload['dateEnd'] = gmdate('Y-m-d H:i:s', $ts);
            }
        }

        $task = espo_request($config, 'POST', 'Task', $taskPayload);
        $taskId = (string) ($task['id'] ?? '');
    }

    return [
        'callId' => $callId,
        'taskId' => $taskId,
        'attachmentId' => $attachmentId,
    ];
}

require_config($config);
auth($config);

$action = (string) ($_GET['action'] ?? '');

try {
    if ($action === 'health') {
        out([
            'ok' => true,
            'service' => 'Parsban Call AI Gateway',
            'crm' => rtrim((string) $config['espo_url'], '/'),
        ]);
    }

    if ($action === 'lookup') {
        $input = json_input();
        $phone = trim((string) ($input['phone'] ?? ''));
        if ($phone === '') out(['ok' => false, 'error' => 'phone is required'], 422);

        out([
            'ok' => true,
            'match' => crm_lookup($config, $phone),
        ]);
    }

    if ($action === 'process_call') {
        if (empty($_FILES['audio']['tmp_name']) || !is_uploaded_file($_FILES['audio']['tmp_name'])) {
            out(['ok' => false, 'error' => 'audio file is required'], 422);
        }

        $meta = [
            'phone' => trim((string) ($_POST['phone'] ?? '')),
            'direction' => trim((string) ($_POST['direction'] ?? 'Incoming')),
            'startedAt' => (int) ($_POST['startedAt'] ?? 0),
            'connectedAt' => (int) ($_POST['connectedAt'] ?? 0),
            'endedAt' => (int) ($_POST['endedAt'] ?? 0),
        ];

        $audio = $_FILES['audio'];
        $tmp = (string) $audio['tmp_name'];
        $name = basename((string) ($audio['name'] ?? 'call-recording.m4a'));
        $mime = (string) ($audio['type'] ?? 'application/octet-stream');

        $match = null;
        $postedType = trim((string) ($_POST['crmType'] ?? ''));
        $postedId = trim((string) ($_POST['crmId'] ?? ''));
        $postedName = trim((string) ($_POST['customerName'] ?? ''));

        if ($postedType !== '' && $postedId !== '') {
            $match = ['type' => $postedType, 'id' => $postedId, 'name' => $postedName];
        } elseif ($meta['phone'] !== '') {
            $match = crm_lookup($config, $meta['phone']);
        }

        $transcription = openai_transcribe($config, $tmp, $name, $mime);
        $summary = openai_summarize($config, $transcription['text'], $meta);

        $crm = crm_save_call(
            $config,
            $match ?? [],
            $meta,
            $summary,
            $transcription['text'],
            $tmp,
            $name,
            $mime
        );

        out([
            'ok' => true,
            'match' => $match,
            'parentName' => (string) ($match['name'] ?? ''),
            'callId' => $crm['callId'],
            'taskId' => $crm['taskId'],
            'attachmentId' => $crm['attachmentId'],
            'summary' => (string) ($summary['summary'] ?? ''),
            'transcript' => $transcription['text'],
            'structured' => $summary,
        ]);
    }

    out(['ok' => false, 'error' => 'Unknown action'], 404);
} catch (Throwable $e) {
    error_log('[Parsban Call AI] ' . $e->getMessage());
    out(['ok' => false, 'error' => $e->getMessage()], 500);
}
