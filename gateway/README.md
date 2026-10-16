# Parsban Call AI Gateway

این پوشه باید روی سروری نصب شود که از بیرون با HTTPS قابل دسترسی باشد.
پیشنهاد برای محیط فعلی:

`https://crm.amniathoshmand.ir/call-ai/api.php`

## نصب

1. فایل `config.example.php` را با نام `config.local.php` کپی کنید.
2. در EspoCRM یک API User اختصاصی بسازید و فقط دسترسی‌های لازم به Account, Contact, Call, Note, Attachment و Task بدهید.
3. API Key آن کاربر را در `config.local.php` قرار دهید.
4. یک OpenAI API key را در همان فایل قرار دهید.
5. مقدار `device_token` را به یک رشته تصادفی طولانی تغییر دهید و همان را در اپ وارد کنید.
6. اطمینان حاصل کنید PHP cURL و file uploads فعال است.

## Endpoint

- `GET api.php?action=health`
- `POST api.php?action=lookup` JSON: `{"phone":"0912..."}`
- `POST api.php?action=process_call` multipart with `audio`

تمام درخواست‌ها باید هدر `X-Parsban-Token` داشته باشند.

فایل ضبط موقت بعد از پردازش نگهداری نمی‌شود. فایل اصلی روی گوشی دست‌نخورده باقی می‌ماند.
