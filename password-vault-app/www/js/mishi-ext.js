/**
 * 密拾 extension — themes, native sync, privacy, settings UI
 */
(function (global) {
  'use strict';

  const MISHI_THEMES = [
    { id: 'naiwan', name: '奶芙粉', swatch: '#FFE4EC', className: 'theme-naiwan' },
    { id: 'sea', name: '海盐蓝', swatch: '#D4EAF7', className: 'theme-sea' },
    { id: 'taro', name: '芋泥紫', swatch: '#E8DFF5', className: 'theme-taro' },
    { id: 'mint', name: '薄荷绿', swatch: '#D8F3E5', className: 'theme-mint' },
    { id: 'cream', name: '奶咖白', swatch: '#F5EDE3', className: 'theme-cream' },
    { id: 'cool', name: '黑粉甜酷', swatch: '#2D2A32', className: 'theme-cool' }
  ];

  const CATEGORY_LIST = ['全部', '社交', '游戏', '购物', '学习', '小众网站', '其他'];
  const AUTO_LOCK_OPTIONS = [
    { value: 10, label: '10 秒' },
    { value: 30, label: '30 秒' },
    { value: 60, label: '1 分钟' },
    { value: 180, label: '3 分钟' },
    { value: 300, label: '5 分钟' },
    { value: 0, label: '关闭' }
  ];

  let autoLockTimer = null;
  let blockAppScreen = false;
  let hooksInstalled = false;

  function $(id) {
    return document.getElementById(id);
  }

  function closeAllDropdowns() {
    document.querySelectorAll('.mishi-dropdown-menu.open').forEach(menu => {
      menu.classList.remove('open');
      const wrap = menu._ownerWrap;
      if (wrap) wrap.classList.remove('open');
      const trigger = wrap?.querySelector('.mishi-dropdown-trigger');
      if (trigger) trigger.setAttribute('aria-expanded', 'false');
      if (menu._ownerWrap && menu.parentNode === document.body) {
        menu._ownerWrap.appendChild(menu);
      }
      menu.style.cssText = '';
    });
  }

  function refreshSelectUI(select) {
    const wrap = select?.closest?.('.mishi-dropdown');
    if (wrap?._syncTrigger) wrap._syncTrigger();
  }

  function enhanceNativeSelect(select) {
    if (!select || select.dataset.mishiEnhanced === '1') return select;
    const parent = select.parentNode;
    if (!parent) return select;

    const wrap = document.createElement('div');
    wrap.className = 'mishi-dropdown';

    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className = 'mishi-dropdown-trigger';
    trigger.setAttribute('aria-haspopup', 'listbox');

    const labelSpan = document.createElement('span');
    labelSpan.className = 'mishi-dropdown-label';
    trigger.appendChild(labelSpan);

    const menu = document.createElement('div');
    menu.className = 'mishi-dropdown-menu';
    menu.setAttribute('role', 'listbox');
    menu._ownerWrap = wrap;

    function buildMenu() {
      menu.innerHTML = '';
      Array.from(select.options).forEach(opt => {
        const active = opt.value === select.value;
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'mishi-dropdown-option' + (active ? ' active' : '');
        btn.dataset.value = opt.value;
        btn.setAttribute('role', 'option');
        btn.setAttribute('aria-selected', active ? 'true' : 'false');
        btn.textContent = opt.textContent.trim();
        btn.addEventListener('click', (ev) => {
          ev.stopPropagation();
          select.value = btn.dataset.value;
          select.dispatchEvent(new Event('change', { bubbles: true }));
          closeAllDropdowns();
          syncTrigger();
          if (navigator.vibrate) navigator.vibrate(6);
        });
        menu.appendChild(btn);
      });
    }

    function syncTrigger() {
      const opt = select.options[select.selectedIndex];
      labelSpan.textContent = opt ? opt.textContent.trim() : '请选择';
      trigger.setAttribute('aria-expanded', menu.classList.contains('open') ? 'true' : 'false');
    }

    function positionMenu() {
      const rect = trigger.getBoundingClientRect();
      menu.style.position = 'fixed';
      menu.style.left = `${Math.max(12, rect.left)}px`;
      menu.style.width = `${rect.width}px`;
      menu.style.zIndex = '1200';
      const spaceBelow = window.innerHeight - rect.bottom - 12;
      const spaceAbove = rect.top - 12;
      if (spaceBelow >= 140 || spaceBelow >= spaceAbove) {
        menu.style.top = `${rect.bottom + 6}px`;
        menu.style.bottom = 'auto';
        menu.style.maxHeight = `${Math.min(280, spaceBelow - 6)}px`;
      } else {
        menu.style.bottom = `${window.innerHeight - rect.top + 6}px`;
        menu.style.top = 'auto';
        menu.style.maxHeight = `${Math.min(280, spaceAbove - 6)}px`;
      }
    }

    function openMenu() {
      closeAllDropdowns();
      buildMenu();
      document.body.appendChild(menu);
      wrap.classList.add('open');
      menu.classList.add('open');
      positionMenu();
      syncTrigger();
    }

    trigger.addEventListener('click', (ev) => {
      ev.stopPropagation();
      if (menu.classList.contains('open')) closeAllDropdowns();
      else openMenu();
    });
    menu.addEventListener('click', ev => ev.stopPropagation());

    select.classList.add('mishi-native-select');
    select.dataset.mishiEnhanced = '1';
    select.style.display = 'none';
    select.tabIndex = -1;
    select.setAttribute('aria-hidden', 'true');
    parent.insertBefore(wrap, select);
    wrap.appendChild(trigger);
    wrap.appendChild(menu);
    wrap.appendChild(select);

    wrap._syncTrigger = syncTrigger;
    wrap._menu = menu;

    syncTrigger();

    const observer = new MutationObserver(() => syncTrigger());
    observer.observe(select, { childList: true, subtree: true, attributes: true, attributeFilter: ['selected'] });

    return select;
  }

  function enhanceAllSelects(root) {
    (root || document).querySelectorAll('select:not([data-mishi-enhanced="1"])').forEach(enhanceNativeSelect);
  }

  function bindDropdownDismiss() {
    if (bindDropdownDismiss.__bound) return;
    bindDropdownDismiss.__bound = true;
    document.addEventListener('click', (ev) => {
      if (ev.target.closest('.mishi-dropdown-trigger') || ev.target.closest('.mishi-dropdown-menu')) return;
      closeAllDropdowns();
    });
    document.addEventListener('keydown', (ev) => {
      if (ev.key === 'Escape') closeAllDropdowns();
    });
    window.addEventListener('scroll', () => {
      document.querySelectorAll('.mishi-dropdown-menu.open').forEach(menu => {
        const wrap = menu._ownerWrap;
        const trigger = wrap?.querySelector('.mishi-dropdown-trigger');
        if (!trigger) return;
        const rect = trigger.getBoundingClientRect();
        menu.style.left = `${Math.max(12, rect.left)}px`;
        menu.style.width = `${rect.width}px`;
      });
    }, true);
    window.addEventListener('resize', closeAllDropdowns);
  }

  function getMetaSafe() {
    if (typeof global.getMeta === 'function') return global.getMeta();
    return null;
  }

  function ensureMetaSettings() {
    const meta = getMetaSafe();
    if (!meta) return null;
    if (!meta.settings || typeof meta.settings !== 'object') {
      meta.settings = {};
    }
    const s = meta.settings;
    if (!s.theme) s.theme = 'sea';
    if (s.theme === 'naiwan') s.theme = 'sea';
    if (s.autoLockSec === undefined) s.autoLockSec = 60;
    if (!s.cardStyle) s.cardStyle = 'card-round';
    if (!Array.isArray(s.activityLog)) s.activityLog = [];
    if (meta.setupComplete === undefined) meta.setupComplete = !!meta.vault;
    return meta;
  }

  function persistMeta(mutator) {
    const meta = ensureMetaSettings();
    if (!meta) return;
    mutator(meta);
    const raw = localStorage.getItem(global.STORAGE_KEY || 'vault_v1');
    const parsed = raw ? JSON.parse(raw) : {};
    localStorage.setItem(global.STORAGE_KEY || 'vault_v1', JSON.stringify({ ...parsed, ...meta }));
  }

  function getSettings() {
    const meta = ensureMetaSettings();
    return meta ? meta.settings : { theme: 'sea', autoLockSec: 60, cardStyle: 'card-round', activityLog: [] };
  }

  function applyTheme(id) {
    const theme = MISHI_THEMES.find(t => t.id === id) || MISHI_THEMES[0];
    const body = document.body;
    if (!body) return;
    MISHI_THEMES.forEach(t => body.classList.remove(t.className));
    body.classList.remove('night-soft');
    body.classList.add(theme.className);
    persistMeta(m => { m.settings.theme = theme.id; });
    document.querySelectorAll('.theme-picker-card[data-theme]').forEach(el => {
      el.classList.toggle('selected', el.dataset.theme === theme.id);
    });
  }

  function pushActivity(action, detail) {
    persistMeta(m => {
      const log = m.settings.activityLog || [];
      log.unshift({ action, detail: detail || '', at: Date.now() });
      m.settings.activityLog = log.slice(0, 50);
    });
    renderActivityLog();
  }

  function renderActivityLog() {
    const ul = $('mishi-activity-log');
    if (!ul) return;
    const log = getSettings().activityLog || [];
    if (!log.length) {
      ul.innerHTML = '<li>暂无记录</li>';
      return;
    }
    ul.innerHTML = log.map(item => {
      const t = new Date(item.at).toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit', month: '2-digit', day: '2-digit' });
      return `<li>${t} · ${item.action}${item.detail ? ' — ' + item.detail : ''}</li>`;
    }).join('');
  }

  function buildSearchIndex() {
    const list = (global.entries || []).filter(e => e.type !== 'screenshot' && !e.imageData);
    return list.map(e => ({
      id: e.id,
      name: e.name || '',
      account: e.account || '',
      url: e.url || '',
      category: e.category || '',
      notes: e.notes || ''
    }));
  }

  async function syncNativeIndex() {
    if (!global.nativePlugin || !global.cryptoKey) return;
    try {
      const json = JSON.stringify(buildSearchIndex());
      await global.nativePlugin.syncSearchIndex({ json });
    } catch (_) {}
  }

  async function syncPendingFromNative() {
    if (!global.nativePlugin || !global.cryptoKey) return;
    try {
      const res = await global.nativePlugin.getPendingEntries();
      const pending = JSON.parse(res.items || '[]');
      if (!pending.length) return;
      const consumed = await global.nativePlugin.consumePendingEntries();
      const items = JSON.parse(consumed.items || '[]');
      if (!items.length) return;
      let added = 0;
      items.forEach(item => {
        const account = item.account || item.username || item.user || '';
        const url = item.url || item.link || '';
        const name = item.name || item.title || account || url || '新拾藏';
        global.entries.unshift({
          id: item.id || (typeof global.uid === 'function' ? global.uid() : Date.now().toString(36)),
          name,
          category: item.category || '其他',
          account,
          url,
          password: item.password || '',
          notes: item.notes || '',
          updatedAt: item.time || Date.now()
        });
        added++;
      });
      if (added && typeof global.saveVault === 'function') {
        await global.saveVault();
        if (typeof global.renderEntries === 'function') global.renderEntries();
        if (typeof global.renderHomeBento === 'function') global.renderHomeBento();
        if (typeof global.renderHomeRecent === 'function') global.renderHomeRecent();
        if (typeof global.toast === 'function') global.toast(`已合并 ${added} 条来自悬浮窗的记录`, 'success');
      }
    } catch (_) {}
  }

  function setPrivacyBlur(active) {
    const el = $('privacy-blur');
    if (!el) return;
    el.classList.toggle('active', !!active);
    el.setAttribute('aria-hidden', active ? 'false' : 'true');
  }

  function resetAutoLockTimer() {
    if (autoLockTimer) clearTimeout(autoLockTimer);
    autoLockTimer = null;
    const sec = getSettings().autoLockSec;
    if (!sec || !global.cryptoKey) return;
    autoLockTimer = setTimeout(() => {
      if (global.cryptoKey && typeof global.lockApp === 'function') global.lockApp();
    }, sec * 1000);
  }

  function bindAutoLock() {
    const handler = () => resetAutoLockTimer();
    ['touchstart', 'keydown', 'click', 'scroll'].forEach(ev => {
      document.addEventListener(ev, handler, { passive: true });
    });
    resetAutoLockTimer();
  }

  function patchCategories() {
    global.CATEGORIES = CATEGORY_LIST.slice();
    const sel = $('f-category');
    if (sel) {
      const opts = CATEGORY_LIST.filter(c => c !== '全部');
      sel.innerHTML = opts.map(c => `<option value="${c}">${c}</option>`).join('');
    }
    if (typeof global.renderCategories === 'function') global.renderCategories();
  }

  function cardClass() {
    return getSettings().cardStyle || 'card-round';
  }

  function patchVaultRowHtml() {
    if (typeof global.vaultRowHtml !== 'function' || global.vaultRowHtml.__mishi) return;
    const orig = global.vaultRowHtml;
    function wrapped(e) {
      const html = orig(e);
      const cls = cardClass();
      return html.replace('class="vault-row"', `class="vault-row ${cls}"`);
    }
    wrapped.__mishi = true;
    global.vaultRowHtml = wrapped;
  }

  function patchOpenDetail() {
    if (typeof global.openDetail !== 'function' || global.openDetail.__mishi) return;
    const orig = global.openDetail;
    global.openDetail = function (id) {
      orig(id);
      const e = (global.entries || []).find(x => x.id === id);
      if (!e || !e.url) return;
      const body = $('detail-body');
      if (!body) return;
      const row = document.createElement('div');
      row.className = 'detail-row';
      row.innerHTML = `
        <div style="flex:1">
          <div class="detail-label">绑定网址</div>
          <div class="detail-value"><span class="detail-url-open" id="detail-url-link">${e.url}</span></div>
        </div>
        <button type="button" class="detail-copy" id="detail-open-url">打开</button>
      `;
      body.appendChild(row);
      const openBtn = $('detail-open-url');
      if (openBtn) {
        openBtn.addEventListener('click', () => {
          const url = e.url;
          if (global.nativePlugin && global.nativePlugin.openUrl) {
            global.nativePlugin.openUrl({ url }).catch(() => {
              window.open(url, '_blank');
            });
          } else {
            window.open(url, '_blank');
          }
        });
      }
    };
    global.openDetail.__mishi = true;
  }

  function patchOpenEdit() {
    if (typeof global.openEdit !== 'function' || global.openEdit.__mishi) return;
    const orig = global.openEdit;
    global.openEdit = function (id) {
      orig(id);
      const urlInput = $('f-url');
      if (!urlInput) return;
      if (id) {
        const e = (global.entries || []).find(x => x.id === id);
        urlInput.value = (e && e.url) || '';
      } else {
        urlInput.value = '';
      }
      refreshSelectUI($('f-category'));
    };
    global.openEdit.__mishi = true;
  }

  function patchSaveButton() {
    const saveBtn = $('save-btn');
    if (!saveBtn || saveBtn.__mishiSave) return;
    saveBtn.addEventListener('click', async (e) => {
      const urlEl = $('f-url');
      if (!urlEl) return;
      e.stopImmediatePropagation();
      e.preventDefault();
      const name = $('f-name')?.value.trim();
      if (!name) {
        if (typeof global.toast === 'function') global.toast('请填写名称', 'error');
        return;
      }
      const editingId = global.editingId;
      const data = {
        id: editingId || (typeof global.uid === 'function' ? global.uid() : Date.now().toString(36)),
        name,
        category: $('f-category')?.value,
        account: ($('f-account')?.value || '').trim(),
        url: urlEl.value.trim(),
        password: $('f-password')?.value || '',
        notes: ($('f-notes')?.value || '').trim(),
        updatedAt: Date.now()
      };
      if (editingId) {
        const idx = global.entries.findIndex(x => x.id === editingId);
        if (idx >= 0) global.entries[idx] = data;
      } else {
        global.entries.push(data);
      }
      await global.saveVault();
      pushActivity('保存', data.name);
      if (typeof global.closeModals === 'function') global.closeModals();
      if (typeof global.renderEntries === 'function') global.renderEntries();
      if (typeof global.toast === 'function') global.toast(editingId ? '已更新' : '已保存', 'success');
      if (typeof global.renderHomeBento === 'function') global.renderHomeBento();
      if (typeof global.renderHomeRecent === 'function') global.renderHomeRecent();
      if (typeof global.updateNavBadges === 'function') global.updateNavBadges();
    }, true);
    saveBtn.__mishiSave = true;
  }

  function patchCopyAndSave() {
    if (typeof global.saveVault === 'function' && !global.saveVault.__mishi) {
      const origSave = global.saveVault;
      global.saveVault = async function () {
        await origSave();
        await syncNativeIndex();
      };
      global.saveVault.__mishi = true;
    }
    if (typeof global.copyText === 'function' && !global.copyText.__mishi) {
      const origCopy = global.copyText;
      global.copyText = async function (text) {
        await origCopy(text);
        pushActivity('复制', (text || '').slice(0, 24));
      };
      global.copyText.__mishi = true;
    }
  }

  function patchShowScreen() {
    if (typeof global.showScreen !== 'function' || global.showScreen.__mishi) return;
    const orig = global.showScreen;
    global.showScreen = function (id) {
      if (id === 'app-screen' && blockAppScreen) {
        orig('theme-picker-screen');
        return;
      }
      orig(id);
    };
    global.showScreen.__mishi = true;
  }

  function patchUnlockFlow() {
    if (typeof global.tryUnlock !== 'function' || global.tryUnlock.__mishi) return;
    const orig = global.tryUnlock;
    global.tryUnlock = async function () {
      await orig();
      if (global.cryptoKey) {
        ensureMetaSettings();
        applyTheme(getSettings().theme);
        await syncNativeIndex();
        await syncPendingFromNative();
        resetAutoLockTimer();
        setPrivacyBlur(false);
      }
    };
    global.tryUnlock.__mishi = true;
  }

  function patchSetupButton() {
    const btn = $('setup-btn');
    if (!btn || btn.__mishiSetup) return;
    const clone = btn.cloneNode(true);
    btn.parentNode.replaceChild(clone, btn);
    clone.addEventListener('click', async () => {
      const pin = $('setup-pin')?.value;
      const confirm = $('setup-pin-confirm')?.value;
      if (!/^\d{4,6}$/.test(pin)) {
        if (typeof global.toast === 'function') global.toast('PIN 须为 4–6 位数字');
        return;
      }
      if (pin !== confirm) {
        if (typeof global.toast === 'function') global.toast('两次 PIN 不一致');
        return;
      }
      await global.setupVault(pin);
      ensureMetaSettings();
      persistMeta(m => { m.setupComplete = false; });
      global.currentPin = pin;
      blockAppScreen = true;
      if (typeof global.showScreen === 'function') global.showScreen('theme-picker-screen');
      if (typeof global.setHeroIllust === 'function') global.setHeroIllust('home');
      if (typeof global.renderCategories === 'function') global.renderCategories();
      if (typeof global.renderEntries === 'function') global.renderEntries();
      if (typeof global.renderHomeBento === 'function') global.renderHomeBento();
      if (typeof global.renderHomeRecent === 'function') global.renderHomeRecent();
      if (typeof global.renderShots === 'function') global.renderShots();
      if (typeof global.syncPendingScreenshots === 'function') global.syncPendingScreenshots();
      if (typeof global.toast === 'function') global.toast('PIN 已设置，请选择主题', 'success');
    });
    clone.__mishiSetup = true;
  }

  function bindThemePickerScreen() {
    const grid = $('theme-picker-grid');
    if (!grid) return;
    grid.innerHTML = MISHI_THEMES.map(t => `
      <div class="theme-picker-card" data-theme="${t.id}" role="button" tabindex="0">
        <div class="theme-swatch" style="background:${t.swatch}"></div>
        <div class="theme-name">${t.name}</div>
      </div>
    `).join('');
    const selected = getSettings().theme || 'sea';
    applyTheme(selected);
    grid.querySelectorAll('.theme-picker-card').forEach(card => {
      const pick = () => applyTheme(card.dataset.theme);
      card.addEventListener('click', pick);
      card.addEventListener('keydown', ev => { if (ev.key === 'Enter') pick(); });
    });
    const finish = $('theme-picker-finish');
    if (finish) {
      finish.addEventListener('click', async () => {
        persistMeta(m => { m.setupComplete = true; });
        blockAppScreen = false;
        applyTheme(getSettings().theme);
        if (typeof global.showScreen === 'function') global.showScreen('app-screen');
        await syncNativeIndex();
        if (typeof global.toast === 'function') global.toast('欢迎使用密拾', 'success');
      });
    }
    const skipPerm = $('theme-picker-skip-perm');
    if (skipPerm) {
      skipPerm.addEventListener('click', () => {
        if (typeof global.toast === 'function') global.toast('可稍后在设置中开启悬浮窗权限');
      });
    }
    const permBtn = $('theme-picker-perm-btn');
    if (permBtn) {
      permBtn.addEventListener('click', () => {
        if (global.nativePlugin && global.nativePlugin.openOverlaySettings) {
          global.nativePlugin.openOverlaySettings();
        }
      });
    }
  }

  function extendSettingsUI() {
    const settings = $('settings-view');
    if (!settings || $('mishi-ext-settings')) return;

    const wrap = document.createElement('div');
    wrap.id = 'mishi-ext-settings';
    wrap.className = 'mishi-ext-settings';
    wrap.innerHTML = `
      <div class="settings-group-label">密拾外观</div>
      <div class="settings-section">
        <div class="settings-item" style="flex-direction:column;align-items:stretch;gap:10px;">
          <div class="item-text"><div>主题贴纸</div><div class="sub">马卡龙配色，随时切换</div></div>
          <div class="theme-picker-grid" id="settings-theme-grid"></div>
        </div>
        <div class="settings-item" style="flex-direction:column;align-items:stretch;gap:8px;">
          <div class="item-text"><div>卡片样式</div></div>
          <select class="settings-select" id="mishi-card-style">
            <option value="card-round">圆润贴纸</option>
            <option value="card-weird">歪歪可爱</option>
            <option value="card-frost">磨砂玻璃</option>
          </select>
        </div>
      </div>
      <div class="settings-group-label">密拾安全</div>
      <div class="settings-section">
        <div class="settings-item" style="flex-direction:column;align-items:stretch;gap:8px;">
          <div class="item-text"><div>自动锁定</div><div class="sub">无操作后自动回到解锁页</div></div>
          <select class="settings-select" id="mishi-auto-lock"></select>
        </div>
        <div class="settings-item" id="mishi-shot-listen-item">
          <div class="item-body">
            <div class="item-text"><div>监听系统截图</div><div class="sub">自动拾取截图到密拾</div></div>
          </div>
          <div class="toggle-switch" id="mishi-shot-listen-toggle"></div>
        </div>
      </div>
      <div class="settings-group-label">活动记录</div>
      <div class="settings-section">
        <ul class="mishi-activity-log" id="mishi-activity-log"></ul>
        <button type="button" class="btn btn-outline" id="mishi-clear-log" style="width:100%;margin-top:8px">清空记录</button>
      </div>
    `;

    const anchor = settings.querySelector('.settings-group-label');
    if (anchor) settings.insertBefore(wrap, anchor);
    else settings.prepend(wrap);

    const grid = $('settings-theme-grid');
    if (grid) {
      grid.innerHTML = MISHI_THEMES.map(t => `
        <div class="theme-picker-card" data-theme="${t.id}">
          <div class="theme-swatch" style="background:${t.swatch}"></div>
          <div class="theme-name">${t.name}</div>
        </div>
      `).join('');
      grid.querySelectorAll('.theme-picker-card').forEach(card => {
        card.addEventListener('click', () => {
          applyTheme(card.dataset.theme);
          if (typeof global.renderEntries === 'function') global.renderEntries();
        });
      });
    }

    const lockSel = $('mishi-auto-lock');
    if (lockSel) {
      lockSel.innerHTML = AUTO_LOCK_OPTIONS.map(o => `<option value="${o.value}">${o.label}</option>`).join('');
      lockSel.value = String(getSettings().autoLockSec ?? 60);
      lockSel.addEventListener('change', () => {
        const v = parseInt(lockSel.value, 10);
        persistMeta(m => { m.settings.autoLockSec = v; });
        resetAutoLockTimer();
      });
    }

    const cardSel = $('mishi-card-style');
    if (cardSel) {
      cardSel.value = getSettings().cardStyle || 'card-round';
      cardSel.addEventListener('change', () => {
        persistMeta(m => { m.settings.cardStyle = cardSel.value; });
        if (typeof global.renderEntries === 'function') global.renderEntries();
      });
    }

    wrap.querySelectorAll('select.settings-select').forEach(sel => enhanceNativeSelect(sel));

    const shotToggle = $('mishi-shot-listen-toggle');
    const shotItem = $('mishi-shot-listen-item');
    if (shotItem && shotToggle && global.nativePlugin) {
      global.nativePlugin.getScreenshotListen().then(res => {
        shotToggle.classList.toggle('on', res.enabled !== false);
      }).catch(() => {});
      shotItem.addEventListener('click', async () => {
        const on = !shotToggle.classList.contains('on');
        shotToggle.classList.toggle('on', on);
        try {
          await global.nativePlugin.setScreenshotListen({ enabled: on });
        } catch (_) {}
      });
    } else if (shotItem) {
      shotItem.style.display = 'none';
    }

    $('mishi-clear-log')?.addEventListener('click', () => {
      persistMeta(m => { m.settings.activityLog = []; });
      renderActivityLog();
    });

    renderActivityLog();
    applyTheme(getSettings().theme);
  }

  function bindPrivacy() {
    document.addEventListener('visibilitychange', () => {
      if (!global.cryptoKey) return;
      setPrivacyBlur(document.hidden);
    });
    /* 保持浅色马卡龙界面，不随系统深色模式变浅文字 */
  }

  function initMishiExt() {
    if (hooksInstalled) return;
    hooksInstalled = true;
    ensureMetaSettings();
    patchCategories();
    patchVaultRowHtml();
    patchOpenDetail();
    patchOpenEdit();
    patchSaveButton();
    patchCopyAndSave();
    patchShowScreen();
    patchUnlockFlow();
    patchSetupButton();
    bindThemePickerScreen();
    extendSettingsUI();
    bindDropdownDismiss();
    enhanceAllSelects();
    bindAutoLock();
    bindPrivacy();
    applyTheme(getSettings().theme);


  }

  global.MISHI_THEMES = MISHI_THEMES;
  global.applyTheme = applyTheme;
  global.initMishiExt = initMishiExt;
  global.refreshSelectUI = refreshSelectUI;
  global.enhanceAllSelects = enhanceAllSelects;
  global.syncNativeIndex = syncNativeIndex;
  global.syncPendingFromNative = syncPendingFromNative;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initMishiExt);
  } else {
    initMishiExt();
  }
})(window);