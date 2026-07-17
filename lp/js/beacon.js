/* beacon.js — LP計測（PIIなし）。イベント辞書は docs/07_event_tracking.md が正典 */
(function () {
  "use strict";

  document.body.classList.add("js"); // リビール演出はJS有効時のみ（無効時は常時表示）

  // === 設定: GASデプロイ後にURLとトークンを書き換える（docs/10参照）===
  // GAS_TOKEN は認証情報ではなく「公開前提のノイズフィルタ」（GitHub Pagesで全訪問者に配信される）。
  // 実際の防御はGAS側のvid別レート制限+append-onlyシート。docs/06/07 と ECCレビューM5参照。
  var GAS_URL = "https://script.google.com/macros/s/AKfycbyRPigNdfReK-ha21G43wUzQOHGycYtwlCDY-f2NLy--tJbmiHLF57B6o2oypDjqBN34g/exec"; // 例: https://script.google.com/macros/s/xxxx/exec
  var GAS_TOKEN = "wvU1F6Q5wQ1EB54tbWJH1KjrOWJXT9MCx3cEBfeD";

  var configured = GAS_URL.indexOf("https://") === 0;

  // 匿名訪問者ID（localStorage、PIIなし）
  function getVid() {
    try {
      var vid = localStorage.getItem("pmh_vid");
      if (!vid) {
        vid = (crypto.randomUUID && crypto.randomUUID()) ||
          "v-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 10);
        localStorage.setItem("pmh_vid", vid);
      }
      return vid;
    } catch (e) {
      return "v-nostorage";
    }
  }

  function uaCoarse() {
    return /Mobi|Android/i.test(navigator.userAgent) ? "mobile" : "desktop";
  }

  function utmParams() {
    var p = new URLSearchParams(location.search);
    return {
      utm_source: p.get("utm_source") || "",
      utm_medium: p.get("utm_medium") || "",
      utm_campaign: p.get("utm_campaign") || ""
    };
  }

  function send(name, params, useKeepalive) {
    var payload = JSON.stringify({
      token: GAS_TOKEN,
      event: name,
      vid: getVid(),
      ts: new Date().toISOString(),
      ua: uaCoarse(),
      params: params || {}
    });
    if (!configured) {
      console.log("[beacon:dry-run]", name, payload);
      return;
    }
    try {
      // sendBeaconはtext/plainでCORSプリフライトを回避（GAS doPostで受信可）
      if (!useKeepalive && navigator.sendBeacon &&
          navigator.sendBeacon(GAS_URL, new Blob([payload], { type: "text/plain" }))) {
        return;
      }
      // page_view等の分母イベントはkeepalive fetchで確実性を上げる
      fetch(GAS_URL, {
        method: "POST", mode: "no-cors", keepalive: true,
        headers: { "Content-Type": "text/plain" }, body: payload
      }).catch(function () {});
    } catch (e) { /* 計測失敗でUXを止めない */ }
  }

  // ---- page_view（分母。keepalive fetch優先）----
  var utm = utmParams();
  send("page_view", {
    referrer: document.referrer || "",
    utm_source: utm.utm_source, utm_medium: utm.utm_medium, utm_campaign: utm.utm_campaign
  }, true);

  // ---- pricing_view（料金セクション50%可視、1回のみ）----
  var pricingSent = false;
  var pricingEl = document.getElementById("pricing");
  if (pricingEl && "IntersectionObserver" in window) {
    new IntersectionObserver(function (entries, obs) {
      entries.forEach(function (en) {
        if (en.isIntersecting && !pricingSent) {
          pricingSent = true;
          send("pricing_view", {});
          obs.disconnect();
        }
      });
    }, { threshold: 0.5 }).observe(pricingEl);
  }

  // ---- cta_click（data-ev属性）----
  document.querySelectorAll("[data-ev='cta_click']").forEach(function (el) {
    el.addEventListener("click", function () {
      send("cta_click", { plan: el.getAttribute("data-plan") || "" });
    });
  });

  // ---- 「購入に進む」→ 未発売モーダル（docs/02 E節共通ルール: 決済は取らない）----
  var modal = document.getElementById("buy-modal");
  document.querySelectorAll(".js-buy").forEach(function (btn) {
    btn.addEventListener("click", function () {
      send("price_choice", { plan: btn.getAttribute("data-plan") || "" });
      openModal();
    });
  });
  // モーダルのフォーカス管理（WAI-ARIA dialogパターン: 移動・Escape・トラップ・復元）
  var lastFocused = null;
  function openModal() {
    if (!modal) return;
    lastFocused = document.activeElement;
    modal.hidden = false;
    document.body.style.overflow = "hidden";
    var first = modal.querySelector("a, button");
    if (first) first.focus();
  }
  function closeModal() {
    if (!modal) return;
    modal.hidden = true;
    document.body.style.overflow = "";
    if (lastFocused && lastFocused.focus) lastFocused.focus();
  }
  if (modal) {
    modal.querySelectorAll(".js-modal-close").forEach(function (el) {
      el.addEventListener("click", closeModal);
    });
    modal.addEventListener("click", function (e) {
      if (e.target === modal) closeModal();
    });
    document.addEventListener("keydown", function (e) {
      if (modal.hidden) return;
      if (e.key === "Escape") {
        closeModal();
        return;
      }
      if (e.key === "Tab") {
        var focusables = modal.querySelectorAll("a, button");
        if (!focusables.length) return;
        var first = focusables[0];
        var last = focusables[focusables.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    });
  }

  // ---- 事前登録フォーム ----
  var form = document.getElementById("prereg-form");
  if (form) {
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var email = (document.getElementById("f-email").value || "").trim();
      if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        var help = document.getElementById("f-email");
        help.setCustomValidity("メールアドレスの形式を確認してください");
        help.reportValidity();
        return;
      }
      var situation = (form.querySelector("input[name='situation']:checked") || {}).value || "";
      var planInterest = (form.querySelector("input[name='plan_interest']:checked") || {}).value || "";
      // Eventsタブにはemailを載せない（docs/07 PII規約）
      send("preregister", { hasEmail: !!email, situation: situation, plan_interest: planInterest });
      // emailはPreregisterタブ専用ペイロードで別送
      if (email) {
        send("preregister_detail", { email: email, situation: situation, plan_interest: planInterest });
      }
      form.hidden = true;
      var done = document.getElementById("prereg-done");
      if (done) done.hidden = false;
    });
    document.getElementById("f-email").addEventListener("input", function () {
      this.setCustomValidity("");
    });
  }

  // ---- leave（visibilitychange時、最大スクロール率）----
  var maxScroll = 0;
  window.addEventListener("scroll", function () {
    var h = document.documentElement.scrollHeight - window.innerHeight;
    if (h > 0) {
      var pct = Math.round((window.scrollY / h) * 100);
      if (pct > maxScroll) maxScroll = pct;
    }
  }, { passive: true });
  document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "hidden") {
      send("leave", { maxScrollPct: maxScroll });
    }
  });

  // ---- カードのスクロールreveal ----
  if ("IntersectionObserver" in window) {
    var revealObs = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) {
          en.target.classList.add("in");
          revealObs.unobserve(en.target);
        }
      });
    }, { threshold: 0.15 });
    document.querySelectorAll(".scene, .feature, .plan").forEach(function (el) {
      revealObs.observe(el);
    });
  } else {
    document.querySelectorAll(".scene, .feature, .plan").forEach(function (el) {
      el.classList.add("in");
    });
  }
})();
