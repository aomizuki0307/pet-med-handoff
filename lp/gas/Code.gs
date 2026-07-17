/**
 * Code.gs — LPビーコン受信エンドポイント（Google Apps Script Webアプリ）
 *
 * gas-membership-kit の webhook.js / eventLog.js パターンの簡素化版:
 *  - URLトークン照合（ダイジェスト比較）
 *  - 全経路200 + 必ずシートに1行記録（GASはHTTPステータス制御不可のため）
 *  - sanitizeForSheet_ による数式インジェクション対策
 *  - append-only（Events / Preregister の2タブ）
 *
 * デプロイ手順は docs/10_setup_firebase.md の「LP計測」節を参照。
 * スクリプトプロパティ:
 *   BEACON_TOKEN   … lp/js/beacon.js の GAS_TOKEN と同じ値（ランダム32文字以上）
 *   SPREADSHEET_ID … 記録先スプレッドシートID
 */

const SHEET_EVENTS = 'Events';
const SHEET_PREREG = 'Preregister';
const TEXT_MAX = 300;

const EVENTS_HEADERS = [
  'received_at', 'vid', 'event', 'plan', 'situation', 'referrer',
  'utm_source', 'utm_medium', 'utm_campaign', 'ua', 'max_scroll_pct', 'client_ts',
];
const PREREG_HEADERS = ['received_at', 'vid', 'email', 'situation', 'plan_interest'];

function doPost(e) {
  try {
    const expected = PropertiesService.getScriptProperties().getProperty('BEACON_TOKEN');
    let payload;
    try {
      payload = JSON.parse(e.postData.contents);
    } catch (err) {
      return json_({ received: true });
    }
    if (!expected || !secureEquals_(String(payload.token || ''), expected)) {
      return json_({ received: true }); // トークン不一致は記録もしない（荒らし対策）
    }

    const p = payload.params || {};
    if (payload.event === 'preregister_detail') {
      // emailを含む行はPreregisterタブのみ（Eventsタブに載せない）
      appendRow_(SHEET_PREREG, PREREG_HEADERS, [
        new Date(),
        s_(payload.vid),
        s_(p.email),
        s_(p.situation),
        s_(p.plan_interest),
      ]);
    } else {
      appendRow_(SHEET_EVENTS, EVENTS_HEADERS, [
        new Date(),
        s_(payload.vid),
        s_(payload.event),
        s_(p.plan || p.plan_interest || ''),
        s_(p.situation || ''),
        s_(p.referrer || ''),
        s_(p.utm_source || ''),
        s_(p.utm_medium || ''),
        s_(p.utm_campaign || ''),
        s_(payload.ua || ''),
        p.maxScrollPct != null ? Number(p.maxScrollPct) : '',
        s_(payload.ts),
      ]);
    }
    return json_({ received: true });
  } catch (err) {
    // 未捕捉例外はHTMLエラーページになるため必ずJSONを返す
    return json_({ received: true, error: true });
  }
}

/** ヘッダ行がなければ作ってから追記（append-only） */
function appendRow_(sheetName, headers, row) {
  const ss = SpreadsheetApp.openById(
    PropertiesService.getScriptProperties().getProperty('SPREADSHEET_ID')
  );
  let sheet = ss.getSheetByName(sheetName);
  if (!sheet) {
    sheet = ss.insertSheet(sheetName);
    sheet.appendRow(headers);
  }
  sheet.appendRow(row);
}

/** 数式インジェクション対策 + 長さ制限 */
function s_(v) {
  let str = String(v == null ? '' : v).slice(0, TEXT_MAX);
  if (/^[=+\-@\t\r]/.test(str)) {
    str = "'" + str;
  }
  return str;
}

/** タイミング攻撃対策のダイジェスト比較 */
function secureEquals_(a, b) {
  const da = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, a);
  const db = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, b);
  if (da.length !== db.length) return false;
  let diff = 0;
  for (let i = 0; i < da.length; i++) diff |= da[i] ^ db[i];
  return diff === 0;
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(
    ContentService.MimeType.JSON
  );
}
