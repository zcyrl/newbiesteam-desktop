package team.newbies.desktop.shell.web

/**
 * Builds the JavaScript shims that give the desktop shells **the exact same web
 * contract as the Android apps**: `window.AndroidBridge`, `__NBT_*` globals,
 * external link interception, native toast + file pick for the dev portal.
 *
 * Everything is idempotent and can be re-injected on every page load.
 */
object JsShims {

    // ----- Public app (NewbiesTeam) -------------------------------------------------

    /** Mirrors MainActivity.injectNativeBridgeState() from the public APK. */
    fun publicBridgeShim(
        versionName: String,
        versionCode: Long,
        appName: String = "NewbiesTeam"
    ): String = """
        (function() {
            try {
                if (window.__NBT_DESKTOP_SHIM__) { return; }
                window.__NBT_DESKTOP_SHIM__ = true;

                window.AndroidBridge = {
                    getAppVersion: function() { return "$versionName"; },
                    getAppVersionCode: function() { return $versionCode; },
                    isAndroidApp: function() { return false; },
                    isDesktopApp: function() { return true; },
                    getAppInfo: function() {
                        return '{"appName":"$appName","versionName":"$versionName","versionCode":$versionCode,"platform":"desktop","isNativeApp":true}';
                    }
                };

                window.__NBT_APP_VERSION__ = "$versionName";
                window.__NBT_BUILD_CODE__ = "$versionCode";
                window.__NBT_IS_APP__ = true;
                window.__NBT_PLATFORM__ = "desktop";

                if (window.dispatchEvent) {
                    window.dispatchEvent(new CustomEvent("nbt:app-ready", {
                        detail: { version: "$versionName", build: "$versionCode", platform: "desktop" }
                    }));
                }
                try {
                    if (typeof syncFooterAppVersion === "function") { syncFooterAppVersion(); }
                } catch (e) { /* page may not have rendered yet */ }

                console.log("NBT desktop bridge ready: $appName $versionName (build $versionCode)");
            } catch (e) {
                console.error("NBT desktop bridge error:", e);
            }
        })();
    """.trimIndent()

    // ----- Dev app (NewbiesDev) ------------------------------------------------------

    /**
     * Mirrors the DevApp AndroidBridge (JS interface) from the dev APK:
     * dev-state persistence, device id, clipboard, toasts, APK download, file pick.
     * kmpJsBridge is used for anything async native side (pick files, downloads).
     */
    fun devBridgeShim(
        versionName: String,
        versionCode: Long,
        deviceId: String,
        diskStateJson: String
    ): String = """
        (function() {
            try {
                if (window.__NBT_DESKTOP_SHIM__) { return; }
                window.__NBT_DESKTOP_SHIM__ = true;

                var APP = "NewbiesDev";
                var VER = "$versionName";
                var BUILD = $versionCode;
                var DISK_STATE = ${jsonJsValue(diskStateJson)};

                /* kmpJsBridge is injected by the shell right after load; calls made
                   before it lands are queued here and flushed by the pump below. */
                if (!window.__NBT_CALL__) {
                    window.__NBT_CALL__ = function(name, params, callback) {
                        var q = window.__NBT_QUEUE__ = window.__NBT_QUEUE__ || [];
                        try {
                            if (window.kmpJsBridge && typeof window.kmpJsBridge.callNative === "function") {
                                window.kmpJsBridge.callNative(name, params || "{}", callback || null);
                                return true;
                            }
                        } catch (e) { /* fall through to queue */ }
                        q.push([name, params || "{}", callback || null]);
                        if (q.length > 32) { q.shift(); }
                        return false;
                    };
                }

                function callNative(name, params, callback) {
                    window.__NBT_CALL__(name, params || "{}", callback || null);
                }

                function showToast(msg) {
                    try {
                        var host = document.body || document.documentElement;
                        if (!host) { return; }
                        var el = document.getElementById("nbt-desktop-toast");
                        if (!el) {
                            el = document.createElement("div");
                            el.id = "nbt-desktop-toast";
                            el.style.cssText = "position:fixed;left:50%;bottom:32px;transform:translateX(-50%);" +
                                "z-index:2147483000;padding:12px 18px;border-radius:12px;color:#f0eee6;" +
                                "background:#1c1717f2;border:1px solid #3a2d2a;box-shadow:0 8px 28px #0008;" +
                                "font:500 14px/1.4 system-ui,sans-serif;max-width:min(86vw,520px);" +
                                "opacity:0;transition:opacity .2s ease;pointer-events:none;text-align:center;";
                            host.appendChild(el);
                        }
                        el.textContent = String(msg || "");
                        el.style.opacity = "1";
                        clearTimeout(el.__nbtT);
                        el.__nbtT = setTimeout(function() { el.style.opacity = "0"; }, 2400);
                    } catch (e) { /* silent */ }
                }

                function lsGet(key) {
                    try { return window.localStorage ? (localStorage.getItem(key) || "") : ""; }
                    catch (e) { return ""; }
                }
                function lsSet(key, val) {
                    try { if (window.localStorage) localStorage.setItem(key, val); } catch (e) {}
                    return true;
                }

                window.AndroidBridge = {
                    getAppVersion: function() { return VER; },
                    getAppVersionCode: function() { return BUILD; },
                    isAndroidApp: function() { return false; },
                    isDesktopApp: function() { return true; },
                    getAppInfo: function() {
                        return '{"appName":"' + APP + '","versionName":"' + VER + '","versionCode":' + BUILD +
                               ',"platform":"desktop","isNativeApp":true}';
                    },

                    getDeviceId: function() { return "$deviceId"; },

                    saveDevState: function(json) {
                        var ok = lsSet("saved_dev_state", String(json || ""));
                        callNative("diskDevStateSave", JSON.stringify({ raw: String(json || "") }), function(data) {});
                        return true;
                    },

                    getDevState: function() {
                        var local = lsGet("saved_dev_state");
                        if (local) { return local; }
                        return DISK_STATE || "";
                    },

                    clearDevState: function() {
                        try { if (window.localStorage) localStorage.removeItem("saved_dev_state"); } catch (e) {}
                        callNative("diskDevStateClear", "{}", function(data) {});
                        DISK_STATE = "";
                        return true;
                    },

                    copyToClipboard: function(text) {
                        try {
                            if (navigator.clipboard && navigator.clipboard.writeText) {
                                navigator.clipboard.writeText(String(text || "")).catch(function() {
                                    callNative("copyToClipboard", JSON.stringify({ text: String(text || "") }), null);
                                });
                            } else {
                                callNative("copyToClipboard", JSON.stringify({ text: String(text || "") }), null);
                            }
                        } catch (e) {
                            callNative("copyToClipboard", JSON.stringify({ text: String(text || "") }), null);
                        }
                        return true;
                    },

                    showToast: showToast,

                    downloadAndInstallApk: function(url, fileName) {
                        callNative("downloadFile", JSON.stringify({ url: String(url || ""), name: String(fileName || "NewbiesDev.apk") }), function(data) {});
                        return true;
                    },

                    pickFiles: function(accept, multiple, callback) {
                        callNative("pickFiles", JSON.stringify({ accept: String(accept || "*/*"), multiple: !!multiple }), function(data) {
                            try {
                                var payload = JSON.parse(data || "{}");
                                var files = payload.files || [];
                                var picked = [];
                                var pending = files.length;
                                var done = function() {
                                    if (callback) { callback(JSON.stringify({ ok: !!(pending === 0), files: picked })); }
                                };
                                if (!pending) { pending = 0; done(); return; }
                                files.forEach(function(f) {
                                    fetch(f.url, { credentials: "same-origin" })
                                        .then(function(r) { return r.blob(); })
                                        .then(function(blob) {
                                            var file = new File([blob], f.name, { type: blob.type || f.type || "application/octet-stream" });
                                            picked.push(file);
                                        })
                                        .catch(function() {})
                                        .finally(function() { pending--; if (pending === 0) { done(); } });
                                });
                            } catch (e) { if (callback) { callback('{"ok":false,"files":[]}'); } }
                        });
                    },

                    saveFile: function(name, contentBase64, callback) {
                        callNative("saveFile", JSON.stringify({ name: String(name || ""), contentBase64: String(contentBase64 || "") }), function(data) {
                            if (callback) { callback('{"ok":true}'); }
                        });
                        return true;
                    }
                };

                /* Route <input type="file"> through the native picker so camera/gallery
                   parity exists on the desktop too (JCEF has no W3C file chooser UI). */
                document.addEventListener("click", function(e) {
                    try {
                        var input = e.target && e.target.closest ? e.target.closest('input[type="file"]') : null;
                        if (!input || input.__nbtBusy) { return; }
                        e.preventDefault();
                        e.stopPropagation();
                        input.__nbtBusy = true;
                        var multiple = input.hasAttribute("multiple");
                        var accept = input.getAttribute("accept") || "*/*";
                        window.AndroidBridge.pickFiles(accept, multiple, function(res) {
                            input.__nbtBusy = false;
                            try {
                                var payload = JSON.parse(res || "{}");
                                var files = payload.files || [];
                                if (!files.length) { return; }
                                var transfer = new DataTransfer();
                                files.forEach(function(f) { transfer.items.add(f); });
                                input.files = transfer.files;
                                input.dispatchEvent(new Event("change", { bubbles: true }));
                            } catch (err) { console.error("NBT desktop pick error", err); }
                        });
                    } catch (err) { /* silent */ }
                }, true);

                window.__NBT_APP_VERSION__ = VER;
                window.__NBT_BUILD_CODE__ = String(BUILD);
                window.__NBT_IS_APP__ = true;
                window.__NBT_PLATFORM__ = "desktop";
                window.__NBT_DEV_STATE_DISK__ = DISK_STATE;

                if (window.dispatchEvent) {
                    window.dispatchEvent(new CustomEvent("nbt:app-ready", {
                        detail: { version: VER, build: String(BUILD), platform: "desktop", app: "NewbiesDev" }
                    }));
                }

                console.log("NBT desktop dev bridge ready: " + APP + " " + VER + " (build " + BUILD + ")");
            } catch (e) {
                console.error("NBT desktop dev bridge error:", e);
            }
        })();
    """.trimIndent()

    // ----- External link / download router -------------------------------------------------

    /**
     * JS mirror of the shared [team.newbies.core.router.UrlRouter] policy:
     * external pages and downloads are opened in the OS browser, hub pages stay
     * in the shell — exactly like the Android WebView clients.
     */
    fun routeInterceptorShim(): String {
        val downloadExtensions = team.newbies.core.router.UrlRouter.DOWNLOAD_EXTENSIONS.joinToString(",") { "\"${it}\"" }
        val externalHosts = team.newbies.core.router.UrlRouter.KNOWN_EXTERNAL_HOSTS.joinToString(",") { "\"${it}\"" }
        val hubHost = team.newbies.core.router.UrlRouter.DEFAULT_HUB_HOST

        return """
        (function() {
            try {
                if (window.__NBT_ROUTE_SHIM__) { return; }
                window.__NBT_ROUTE_SHIM__ = true;

                /* Native-call dispatcher + queue pump (idempotent; shared with the
                   bridge shim): every routeLink/reloadPage call goes through here
                   so early calls made before the shell injects kmpJsBridge are
                   flushed in order, exactly once. */
                if (!window.__NBT_CALL__) {
                    window.__NBT_CALL__ = function(name, params, callback) {
                        var q = window.__NBT_QUEUE__ = window.__NBT_QUEUE__ || [];
                        try {
                            if (window.kmpJsBridge && typeof window.kmpJsBridge.callNative === "function") {
                                window.kmpJsBridge.callNative(name, params || "{}", callback || null);
                                return true;
                            }
                        } catch (e) { /* fall through to queue */ }
                        q.push([name, params || "{}", callback || null]);
                        if (q.length > 32) { q.shift(); }
                        return false;
                    };
                }
                if (!window.__NBT_PUMP__) {
                    window.__NBT_PUMP__ = function() {
                        try {
                            if (window.kmpJsBridge && window.__NBT_QUEUE__ && window.__NBT_QUEUE__.length) {
                                var q = window.__NBT_QUEUE__;
                                window.__NBT_QUEUE__ = [];
                                for (var i = 0; i < q.length; i++) {
                                    try {
                                        window.kmpJsBridge.callNative(q[i][0], q[i][1], q[i][2]);
                                    } catch (err2) { /* drop */ }
                                }
                            }
                        } catch (err) { /* silent */ }
                    };
                    var pumpTries = 0;
                    var pumpTimer = setInterval(function() {
                        window.__NBT_PUMP__();
                        if (++pumpTries > 100) { clearInterval(pumpTimer); }
                    }, 80);
                }

                var DL_EXT = [$downloadExtensions];
                var EXT_HOSTS = [$externalHosts];
                var HUB_HOSTS = ["$hubHost", "newbiesbase.surge.sh", "localhost", "127.0.0.1"];

                function routeKind(url) {
                    try {
                        if (!url || url.length < 2) { return "internal"; }
                        var lower = url.toLowerCase();
                        if (lower.indexOf("intent:") === 0 || lower.indexOf("tel:") === 0 ||
                            lower.indexOf("mailto:") === 0 || lower.indexOf("tg:") === 0) { return "intent"; }
                        var protoSplit = lower.indexOf("://");
                        var rest = protoSplit >= 0 ? lower.substring(protoSplit + 3) : lower;
                        var slash = rest.indexOf("/");
                        var hostPath = slash >= 0 ? rest : rest + "/";
                        var host = hostPath.split("/")[0].split(":")[0];
                        var path = hostPath;

                        for (var i = 0; i < DL_EXT.length; i++) {
                            if (path.indexOf(DL_EXT[i]) !== -1) { return "download"; }
                        }
                        for (var j = 0; j < EXT_HOSTS.length; j++) {
                            if (host === EXT_HOSTS[j] || host.indexOf("." + EXT_HOSTS[j]) === host.length - EXT_HOSTS[j].length - 1) {
                                return "external";
                            }
                        }
                        for (var k = 0; k < HUB_HOSTS.length; k++) {
                            if (host === HUB_HOSTS[k] || host.indexOf("." + HUB_HOSTS[k]) === host.length - HUB_HOSTS[k].length - 1) {
                                return "internal";
                            }
                        }
                        return "external";
                    } catch (e) { return "external"; }
                }

                function sendToNative(kind, url, targetName) {
                    return window.__NBT_CALL__(
                        "routeLink",
                        JSON.stringify({ url: url, kind: kind, label: targetName || "" }),
                        null
                    );
                }

                function openExternal(url) {
                    if (sendToNative(routeKind(url), url)) { return; }
                    try { window.open(url, "_blank"); } catch (e) {}
                }

                window.__NBT_DESKTOP_OPEN__ = openExternal;

                document.addEventListener("click", function(e) {
                    try {
                        if (e.defaultPrevented || (e.button !== 0 && e.button !== undefined)) { return; }
                        if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) { return; }
                        var el = e.target && e.target.closest ? e.target.closest("a[href]") : null;
                        if (!el) { return; }
                        var href = el.getAttribute("href");
                        if (!href) { return; }
                        var abs = el.href || href;
                        var kind = routeKind(abs);
                        if (kind === "internal") { return; }
                        e.preventDefault();
                        e.stopPropagation();
                        if (el.target === "_blank" || !sendToNative(kind, abs, el.textContent)) {
                            try { window.open(abs, el.target || "_blank"); } catch (err) {}
                        }
                    } catch (err) { /* silent */ }
                }, true);

                var nativeOpen = window.open;
                window.open = function(url, name) {
                    try {
                        if (typeof url === "string" && url.length > 0) {
                            var kind = routeKind(url);
                            if (kind !== "internal") {
                                if (sendToNative(kind, url, name)) { return null; }
                            }
                        }
                    } catch (e) { /* fallthrough */ }
                    return nativeOpen.apply(window, arguments);
                };

                // Swipe-to-refresh parity: F5 / Ctrl+R reloads the shell page.
                document.addEventListener("keydown", function(e) {
                    try {
                        var isF5 = e.key === "F5";
                        var isCtrlR = (e.ctrlKey || e.metaKey) && (e.key === "r" || e.key === "R");
                        if (isF5 || isCtrlR) {
                            // Queued if the bridge is not live yet; the shell reloads.
                            e.preventDefault();
                            window.__NBT_CALL__("reloadPage", "{}", null);
                        }
                    } catch (err) { /* silent */ }
                }, true);

                console.log("NBT desktop router shim active");
            } catch (e) {
                console.error("NBT desktop router shim error:", e);
            }
        })();
        """.trimIndent()
    }

    private fun jsonJsValue(raw: String): String {
        if (raw.isBlank()) return "\"\""
        val escaped = raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("</script>", "<\\/script>")
        return "\"$escaped\""
    }
}
