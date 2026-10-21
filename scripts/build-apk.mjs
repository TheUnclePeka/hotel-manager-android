// Сборка подписанного APK: apps/android/release/HotelManager-<версия>.apk
// Требуется JDK 21 и Android SDK (переменные JAVA_HOME и ANDROID_HOME), см. docs/ADMIN.md.
import { execFileSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const android = path.join(root, 'android');
const version = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8')).version;
const win = process.platform === 'win32';

// Если переменные не заданы — берём JDK и SDK из %LOCALAPPDATA%\HotelManagerBuild (так они ставятся по docs/ADMIN.md).
const local = path.join(process.env.LOCALAPPDATA ?? '', 'HotelManagerBuild');
if (!process.env.JAVA_HOME && fs.existsSync(path.join(local, 'jdk21'))) process.env.JAVA_HOME = path.join(local, 'jdk21');
if (!process.env.ANDROID_HOME && !process.env.ANDROID_SDK_ROOT && fs.existsSync(path.join(local, 'android-sdk'))) process.env.ANDROID_HOME = path.join(local, 'android-sdk');

for (const v of ['JAVA_HOME', 'ANDROID_HOME']) {
  if (!process.env[v] && !(v === 'ANDROID_HOME' && process.env.ANDROID_SDK_ROOT)) {
    console.error(`Не задана переменная ${v}. Установите JDK 21 и Android SDK (docs/ADMIN.md, раздел 6).`);
    process.exit(1);
  }
}
fs.writeFileSync(path.join(android, 'local.properties'), `sdk.dir=${(process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT).replace(/\\/g, '\\\\')}\n`);

// Ключ подписи создаётся один раз. Без него нельзя выпустить обновление поверх установленного приложения!
const ksProps = path.join(android, 'keystore.properties');
if (!fs.existsSync(ksProps)) {
  const pass = crypto.randomBytes(18).toString('base64url');
  const keytool = path.join(process.env.JAVA_HOME, 'bin', win ? 'keytool.exe' : 'keytool');
  execFileSync(keytool, ['-genkeypair', '-v', '-keystore', path.join(android, 'hotelmanager-release.jks'), '-alias', 'hotelmanager', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10000', '-storepass', pass, '-keypass', pass, '-dname', 'CN=HotelManager, O=HotelManager, C=RU'], { stdio: 'inherit' });
  fs.writeFileSync(ksProps, `storeFile=hotelmanager-release.jks\nstorePassword=${pass}\nkeyAlias=hotelmanager\nkeyPassword=${pass}\n`);
  console.log('\n!!! Создан ключ подписи apps/android/android/hotelmanager-release.jks и keystore.properties.');
  console.log('!!! Сохраните оба файла в надёжном месте — без них обновить приложение на телефоне будет нельзя.\n');
}

execFileSync(path.join(android, win ? 'gradlew.bat' : 'gradlew'), ['assembleRelease', '--no-daemon'], { cwd: android, stdio: 'inherit', shell: win });
const apk = path.join(android, 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
const out = path.join(root, 'release', `HotelManager-${version}.apk`);
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.copyFileSync(apk, out);
console.log(`Готово: ${out}`);
