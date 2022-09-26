export default class CustomLoginApi {
	constructor(e) {
		this.proxyUrl = e
	}
	login(e, t, r, n, o, s) {
		if (!e || !t) throw new Error("Please specify all mandatory parameters!");
		const i = Helpers.getLoginRequest(this.proxyUrl, e, t, r, s),
			a = Helpers.getLoginRequest(this.proxyUrl, e, t, r, s),
			libaryId = s.libaryId
		Helpers.fetchRequest(i).then(e => {
			e.ok ? n ? n(libaryId) : this.redirectToTargetUrl() : Helpers.fetchRequest(a).then((function(e) {
				if (e.ok) n ? n(libaryId) : this.redirectToTargetUrl();
				else {
					if (!(o instanceof Function)) throw new Error("Login request failed");
					o(e)
				}
			}), (function(e) {
				if (!(o instanceof Function)) throw new Error("Login request failed");
				o(e)
			}))
		})
	}
	redirectToWebAuthProvider(e, t) {
		const r = new URLSearchParams(window.location.search).get("targetUrl"),
			n = Object.assign({}, e && {
				provider: e
			}, r && {
				targetUrl: r
			}),
			o = new Headers({
				"Content-Type": "application/json"
			}),
			s = new Request((this.proxyUrl || "") + "/spotfire/rest/pub/authenticationEndpoint", {
				method: "POST",
				credentials: "include",
				headers: o,
				body: JSON.stringify(n)
			});
		return Helpers.fetchRequest(s).then(e => {
			if (!e.ok) {
				if (!(t instanceof Function)) throw Error("Unable to redirect to the web authentication provider!", e);
				t(e)
			}
			return e.json()
		}).then(e => window.location.href = e.authenticationEndpoint)
	}
	redirectToTargetUrl(targetUrl) {
		//const e = new URLSearchParams(window.location.search).get("targetUrl");
		const e = targetUrl;
		if (e) return void(window.location.href = e);
		const t = new Request((this.proxyUrl || "") + "/spotfire/rest/pub/headerConfig", {
			method: "GET",
			credentials: "include"
		});
		Helpers.fetchRequest(t).then(e => e.json()).then(e => {
			e && e.accessibleApps && 1 === e.accessibleApps.length ? window.location.href = e.accessibleApps[0].path || "" :
				window.location.href = e ? e.defaultRedirectUrl : ""
		})
	}
	getServerManifest(e) {
		const t = new Request((this.proxyUrl || "") + "/spotfire/rest/pub/api/manifest", {
			method: "GET",
			credentials: "include"
		});
		return Helpers.fetchRequest(t).then(t => {
			if (!t.ok) {
				if (!(e instanceof Function)) throw new Error("Unable to get server manifest!", t);
				e(t)
			}
			return t.json()
		})
	}
}
class Helpers {
	static fetchRequest(e) {
		if (!e) throw new Error("Please specify all mandatory parameters!");
		const t = Helpers.getMandatoryRequestHeaders();
		return Object.keys(t).forEach((function(r) {
			e.headers.append(r, t[r])
		})), fetch(e)
	}
	static getMandatoryRequestHeaders() {
		const e = Helpers.readCookie("XSRF-TOKEN");
		return Object.assign({}, e && {
			"X-XSRF-TOKEN": e
		}, {
			"X-Requested-With": "XMLHttpRequest"
		}, {
			Accept: "application/json, text/plain, */*"
		})
	}
	static readCookie(e) {
		const t = document.cookie.split(";");
		for (let r of t)
			if (r = r.trim(), 0 === r.indexOf(e + "=")) return decodeURIComponent(r.split("=")[1]);
		return ""
	}
	static getLoginRequest(e, t, r, n, o) {
		const s = "&sf_remember_me=" + n,
			i = o ? "&" + Object.keys(o).filter(e => -1 !== e.indexOf("sf_custom_login_")).map(e => `${e}=${o[e]}`).join("&") :
			"",
			a = new Headers({
				"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"
			});
		return new Request((e || "") + "/spotfire/sf_security_check", {
			method: "POST",
			credentials: "include",
			headers: a,
			body: `sf_username=${t}&sf_password=${r}${s}${i}`
		})
	}
}
