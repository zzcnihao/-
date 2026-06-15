(function (global) {
  'use strict';

  let workerPromise = null;
  let workerBusy = false;
  const ocrQueue = [];

  const ACCOUNT_LABELS = /^(账号|帐户|用户名|用户|登录名|手机号|手机|邮箱|帐号|account|username|user\s*name|email|phone|mobile|login)/i;
  const PASSWORD_LABELS = /^(密码|passwd|password|pwd|pass|密\s*码)/i;
  const NAME_LABELS = /^(程序|应用|网站|站点|平台|app|site|service)/i;
  const URL_LABELS = /^(网址|网站|链接|域名|地址|url|link|website|homepage|domain|site)/i;
  const URL_PATTERN = /(?:https?:\/\/|www\.)[^\s<>"{}|\\^`[\]]+/i;
  const DOMAIN_PATTERN = /(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:com|cn|net|org|io|app|dev|me|tv|cc|xyz|shop|top|info|co|uk|jp|kr|hk|tw|edu|gov)(?:[/?#][^\s]*)?/i;

  function normalizeText(text) {
    return (text || '')
      .replace(/\r/g, '\n')
      .replace(/[|｜]/g, ' ')
      .replace(/[０-９]/g, ch => String.fromCharCode(ch.charCodeAt(0) - 0xFEE0))
      .replace(/[Ａ-Ｚａ-ｚ]/g, ch => String.fromCharCode(ch.charCodeAt(0) - 0xFEE0));
  }

  function cleanValue(val) {
    return (val || '')
      .replace(/^[\s:：\-—]+/, '')
      .replace(/[\s:：]+$/, '')
      .trim();
  }

  function isEmail(val) {
    return /^[\w.+-]+@[\w-]+\.[\w.+-]+$/.test(val || '');
  }

  function normalizeUrl(val) {
    let url = cleanValue(val).replace(/[，。；;,]+$/g, '');
    if (!url) return '';
    if (isEmail(url)) return '';
    if (/^https?:\/\//i.test(url)) return url;
    if (/^www\./i.test(url)) return 'https://' + url;
    if (DOMAIN_PATTERN.test(url)) return 'https://' + url.replace(/^\/\//, '');
    return '';
  }

  function extractUrlFromText(raw) {
    const httpMatch = raw.match(URL_PATTERN);
    if (httpMatch) return normalizeUrl(httpMatch[0]);
    const domainMatch = raw.match(DOMAIN_PATTERN);
    if (domainMatch) return normalizeUrl(domainMatch[0]);
    return '';
  }

  function parseCredentials(text) {
    const raw = normalizeText(text);
    const lines = raw.split(/\n+/).map(l => l.trim()).filter(Boolean);
    let account = '';
    let password = '';
    let name = '';
    let url = '';

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const inline = line.match(/^(.{1,14})[:：]\s*(.+)$/);
      if (inline) {
        const label = inline[1].trim();
        const val = cleanValue(inline[2]);
        if (!val) continue;
        if (!url && URL_LABELS.test(label)) url = normalizeUrl(val) || extractUrlFromText(val);
        else if (!account && ACCOUNT_LABELS.test(label)) account = val;
        else if (!password && PASSWORD_LABELS.test(label)) password = val;
        else if (!name && NAME_LABELS.test(label)) name = val;
        continue;
      }
      const spaced = line.match(/^(.{1,14})\s+([^\s].+)$/);
      if (spaced) {
        const label = spaced[1].trim();
        const val = cleanValue(spaced[2]);
        if (!url && URL_LABELS.test(label)) url = normalizeUrl(val) || extractUrlFromText(val);
        else if (!account && ACCOUNT_LABELS.test(label)) account = val;
        else if (!password && PASSWORD_LABELS.test(label)) password = val;
        else if (!name && NAME_LABELS.test(label)) name = val;
      }
      if (!url) {
        const inlineUrl = normalizeUrl(line) || extractUrlFromText(line);
        if (inlineUrl && !PASSWORD_LABELS.test(line) && !ACCOUNT_LABELS.test(line)) url = inlineUrl;
      }
    }

    for (let i = 0; i < lines.length; i++) {
      if (PASSWORD_LABELS.test(lines[i])) {
        const inlinePwd = lines[i].replace(/^[^:：]+[:：\s]*/, '');
        if (!password && inlinePwd.length >= 4) password = cleanValue(inlinePwd);
        else if (!password && lines[i + 1] && lines[i + 1].length >= 4 && !ACCOUNT_LABELS.test(lines[i + 1]) && !URL_LABELS.test(lines[i + 1])) {
          password = cleanValue(lines[i + 1]);
        }
      }
      if (!account && ACCOUNT_LABELS.test(lines[i])) {
        const inlineAcc = lines[i].replace(/^[^:：]+[:：\s]*/, '');
        if (inlineAcc.length >= 3) account = cleanValue(inlineAcc);
        else if (lines[i + 1] && lines[i + 1].length >= 3 && !URL_LABELS.test(lines[i + 1])) account = cleanValue(lines[i + 1]);
      }
      if (!url && URL_LABELS.test(lines[i])) {
        const inlineUrlVal = lines[i].replace(/^[^:：]+[:：\s]*/, '');
        url = normalizeUrl(inlineUrlVal) || extractUrlFromText(inlineUrlVal);
        if (!url && lines[i + 1]) url = normalizeUrl(lines[i + 1]) || extractUrlFromText(lines[i + 1]);
      }
    }

    if (!url) url = extractUrlFromText(raw);
    if (!account) {
      const email = raw.match(/[\w.+-]+@[\w-]+\.[\w.+-]+/);
      if (email) account = email[0];
    }
    if (!account) {
      const phone = raw.match(/(?:\+?86[-\s]?)?1[3-9]\d{9}/);
      if (phone) account = phone[0].replace(/[-\s]/g, '');
    }
    if (!password) {
      const pwdMatch = raw.match(/(?:密码|password|pwd)[:：\s]*([^\s\n]{4,32})/i);
      if (pwdMatch) password = cleanValue(pwdMatch[1]);
    }

    return { account, password, name, url, raw };
  }

  async function getWorker() {
    if (workerPromise) return workerPromise;
    workerPromise = (async () => {
      if (!global.Tesseract) throw new Error('OCR engine missing');
      const worker = await global.Tesseract.createWorker('chi_sim+eng', 1, {
        workerPath: 'js/vendor/worker.min.js',
        langPath: 'https://tessdata.projectnaptha.com/4.0.0',
        corePath: 'https://cdn.jsdelivr.net/npm/tesseract.js-core@5.0.0/tesseract-core-simd-lstm.wasm.js',
        logger: () => {}
      });
      return worker;
    })();
    return workerPromise;
  }

  async function runOcr(base64) {
    const worker = await getWorker();
    const dataUrl = base64.startsWith('data:') ? base64 : 'data:image/jpeg;base64,' + base64;
    const { data } = await worker.recognize(dataUrl);
    return data.text || '';
  }

  function runQueued(task) {
    return new Promise((resolve, reject) => {
      ocrQueue.push({ task, resolve, reject });
      drainQueue();
    });
  }

  async function drainQueue() {
    if (workerBusy || !ocrQueue.length) return;
    workerBusy = true;
    const { task, resolve, reject } = ocrQueue.shift();
    try {
      resolve(await task());
    } catch (e) {
      reject(e);
    } finally {
      workerBusy = false;
      drainQueue();
    }
  }

  async function recognizeCredentials(base64) {
    return runQueued(async () => {
      const text = await runOcr(base64);
      const parsed = parseCredentials(text);
      return Object.assign({}, parsed, { text });
    });
  }

  global.MishiOcr = {
    parseCredentials,
    recognizeCredentials
  };
})(window);
