# نسخه سفارشی AndroidHarness — مدل‌های محلی

این شاخه طراحی Kotlin/Compose و صفحه‌های اصلی AndroidHarness را حفظ می‌کند؛ بازطراحی یا جایگزینی با WebView انجام نشده است.

## مدل‌های سبک جدید

مدل‌ها از همان کاتالوگ و فیلتر موجود Uncensored/Abliterated قابل انتخاب هستند. تمام فایل‌های زیر Q4_K_M و GGUF تک‌فایلی‌اند. «بدون سانسور» برچسب منتشرکنندهٔ مدل است، نه تضمین عدم امتناع یا صحت پاسخ.

| مدل | حجم فایل تقریبی، MB ده‌دهی |
|---|---:|
| Qwen 3 0.6B Uncensored | 397 |
| Qwen 2.5 0.5B Uncensored | 491 |
| Llama 3.2 1B Abliterated | 955 |
| Qwen 2.5 1.5B Uncensored | 1117 |
| Qwen 2.5 3B Uncensored Test | 1930 |

نسخه، اندازه، SHA-256، معماری و وجود chat template در تاریخ 2026-11-05 از منابع Hugging Face بررسی شده‌اند. بررسی metadata جای اجرای واقعی تمام مدل‌ها را نمی‌گیرد. RAM موردنیاز از حجم فایل بیشتر است؛ برای شروع مدل 0.5B یا 0.6B و context کم را انتخاب کنید.

## افزودن مدل از Hugging Face

1. تنظیمات مدل‌های محلی را باز کنید؛ از بخش افزودن مدل سفارشی با لینک استفاده کنید.
2. لینک مخزن عمومی GGUF یا لینک فایل با `blob` یا `resolve` را وارد کنید.
3. در لینک مخزن، فایل مناسب را از فهرست انتخاب کنید. ترجیحاً یک مدل Instruct/Chat با quantization برابر Q4_K_M.
4. دانلود را اجرا کنید؛ پیشرفت، لغو، ادامهٔ دانلود معتبر و خطای شبکه از همان رابط اصلی نمایش داده می‌شوند.
5. بعد از نصب تأییدشده، مدل در فهرست نصب‌شده‌ها قابل انتخاب است. انتخاب مدل، وزن‌ها را ناخواسته load نمی‌کند؛ برای preload دکمه Load روی مدل انتخاب‌شده یا ارسال پیام را استفاده کنید.

نمونهٔ لینک عمومی:

`https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF`

این مسیر GGUF تک‌فایلی را می‌پذیرد؛ لینک projector و GGUF چندبخشی به‌عنوان مدل چت پذیرفته نمی‌شوند. فرمت Safetensors مسیر واردسازی/تبدیل مجزای موجود و محدودیت‌های خودش را دارد؛ هر مدل Hugging Face قابل اجرای مستقیم نیست. مخازن gated/private در مسیر لینک عمومی پشتیبانی نشده‌اند.

## اصلاحات دانلود و پایداری

- Resume فقط با Content-Range صحیح؛ جلوگیری از append شدن پاسخ کامل به فایل ناقص.
- identity encoding، timeout اتصال/خواندن و لغو تماس مسدودشده.
- انتقال streaming و بررسی اندازه و checksum؛ prefix سالم در قطع شبکه حفظ می‌شود.
- برای لینک مستقیم فاقد checksum، ادامه از prefix قبلی عمداً غیرفعال است تا دو نسخه متفاوت فایل ترکیب نشوند.
- محدودیت دفاعی context/token/thread، بررسی ABI و RAM آزاد و جلوگیری از گسترش ضمنی KV cache.
- خطای قابل‌بازیابی JNI به رابط برمی‌گردد؛ cache نیمه‌ساخته آزاد می‌شود.
- GPU runtime همان build می‌تواند در APK قرار بگیرد؛ نصب آن به انتشار فایل سفارشی در GitHub سازنده وابسته نیست.

**مرز تضمین:** موتور native هنوز داخل process برنامه است. SIGSEGV، خرابی driver و kill شدن توسط Android را نمی‌توان با try/catch مهار کرد. بدون تست دانلود/اجرای APK روی گوشی هدف، «تمام کرش‌ها رفع شد» ادعای معتبری نیست.

## Harnessها

Settings → Coding → External harnesses اتصال SSH برای بازبینی با Claude Code، Codex و Pi دارد. CLI باید روی میزبان SSH نصب و وارد حساب شده باشد؛ این قابلیت نصب CLI محلی اندروید یا جایگزینی کامل Code engine نیست. جزئیات در `external-harnesses.md` آمده است. DeepSea تا مشخص‌شدن پروژه/پروتکل دقیق غیرفعال است و DeepSeek فرض نشده است.

## ساخت از سورس

JDK 21، Android SDK 37.0، NDK 27.2.12479018، CMake 3.22.1 و glslc لازم‌اند.

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug -PnativeAbis=arm64-v8a -PrequireNative=true -PbundleGpuRuntime=true --max-workers=1
```

این خروجی فقط ARM64 است و debug applicationId مستقل `com.androidharness.app.debug` دارد. امضای محلی با امضای upstream یکسان نیست؛ نسخه اصلی را برای نصب این build حذف نکنید. برای انتشار عمومی باید keystore اختصاصی و پشتیبان امن آن داشته باشید.

برای شبکه‌های دارای مشکل CDN، متغیر `HARNESS_NATIVE_ARCHIVES` می‌تواند به پوشهٔ آرشیوهای اصلی `vulkan.tar.gz`، `spirv.tar.gz`، `llama.tar.gz` و `kleidiai.tar.gz` اشاره کند؛ hashهای پین‌شده همچنان بررسی می‌شوند.
