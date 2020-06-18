let lastResponseGlobal = { };
let mech;
let c2c = { };

const
	target = "<all_urls>"
	, pendingRequests = []
	/*
	On startup, connect to the "sasl" app.
	*/
	, port = browser.runtime.connectNative("sasl")
	, RE_SASL_MECH = "[A-Z0-9-_]{1,20}"
	, RE_MECHSTRING = "\"(" + RE_SASL_MECH + "(?:[ ]" + RE_SASL_MECH + ")*)\""
	, RE_DNSSTRING = "\"([a-zA-Z0-9-_]+(?:\\.[a-zA-Z0-9-_]+)+)\""
	, RE_BWS = "[ \\t]*"
	, RE_OWS = RE_BWS
	, RE_TOKEN68 = "([a-zA-Z0-9-._~+/]+=*)"
	, RE_AUTH_PARAM =
    "(?:" +
        "([CcSs][2][CcSs])" + RE_BWS + "=" + RE_BWS + RE_TOKEN68 +
        "|" +
        "([Mm][Ee][Cc][Hh])" + RE_BWS + "=" + RE_BWS + RE_MECHSTRING +
        "|" +
        "([Rr][Ee][Aa][Ll][Mm])" + RE_BWS + '=' + RE_BWS + RE_DNSSTRING +
    ")"
	, RE_AUTH_SCHEME = "[Ss][Aa][Ss][Ll]"
	, RE_CREDENTIALS = RE_AUTH_SCHEME + "(?:[ ]+(" + RE_AUTH_PARAM + "(?:" +
        RE_OWS + "," + RE_OWS + RE_AUTH_PARAM + ")+)?)"
	, parseSasl = (input) => {
		//console.log(input);
		//console.log(RE_CREDENTIALS);
		const regexp1 = new RegExp(RE_CREDENTIALS);
		if (regexp1.test(input)) {
			//console.log(RE_AUTH_PARAM);
			const regexp2 = new RegExp(RE_AUTH_PARAM, "g");
			let result;
			const map = { };
			while (result = regexp2.exec(input)) {
				//console.log(result);
				for (let i = 1; i < result.length; i += 2) {
					if (result[i]) {
						map[result[i]] = result[i + 1];
					}
				}
			}
			//console.log(map);
			return map;
		} else {
			console.log("No match");
		}
	}, completed = (requestDetails) => {
		/*
		 A request has completed. We can stop worrying about it.
		 */
		console.log("completed: " + requestDetails.requestId);
		const index = pendingRequests.indexOf(requestDetails.requestId);
		if (index > -1) {
			pendingRequests.splice(index, 1);
		}
		mech = undefined;
		delete c2c[requestDetails.requestId];
	}, asyncRedirect = (attrs, requestId, obj) => {
		return new Promise((resolve, reject) => {
			const
				portListener = (response) => {
					console.log("response " + requestId + ": " + JSON.stringify(response));
					lastResponseGlobal[requestId] = response;
					port.onMessage.removeListener(portListener);
					resolve(obj);
				}
				;

			console.log("posting " + requestId + ": " + JSON.stringify(attrs));
			port.onMessage.addListener(portListener);
			port.postMessage(attrs);
		});
	}, binaryToHex = (binary) => {
		let result = "";
		for (let i = 0; i < binary.length; i++) {
			result += ("00" + binary.charCodeAt(i).toString(16)).slice(-2) + " ";
		}
		return result;
	}, saslDataToString = (str) => {
		const data = atob(str);
		return mech === "DIGEST-MD5" ? data : binaryToHex(data);
	}, onHeadersReceived = (requestDetails) => {
		let i;
		const responseHeaders = requestDetails.responseHeaders;
		for (i = 0; i < responseHeaders.length; i++) {
			responseHeaders[responseHeaders[i].name] = responseHeaders[i];
		}
		console.log("-----------------");
		console.log("onHeadersReceived");
		console.log(requestDetails);
		const authenticate = responseHeaders["WWW-Authenticate"];
		if (authenticate) {
			const attrs = parseSasl(authenticate.value);
			const requestId = requestDetails.requestId;
			console.log("Status code: " + requestDetails.statusCode);
			console.log(attrs);

			if (attrs.mech) {
				mech = attrs.mech;
				console.log("mech: " + mech);
			}
			if (attrs.s2c) {
				console.log("s2c: " + saslDataToString(attrs.s2c));
			}
			if (attrs.s2s) {
				console.log("s2s: " + atob(attrs.s2s));
			}
			if (c2c[requestId]) {
				console.log("c2c: " + atob(c2c[requestId]));
				attrs.c2c = c2c[requestId];
			}
			if (requestDetails.statusCode == 401) {
				if (pendingRequests.indexOf(requestId) != -1) {
					console.log("phase 2: " + requestId);
					return asyncRedirect(attrs, requestId, {
						redirectUrl: requestDetails.url
					});
				} else {
					pendingRequests.push(requestId);
					console.log("phase 1: " + requestId);
					return asyncRedirect(attrs, requestId, {
						redirectUrl: requestDetails.url
					});
				}

			} else {
				console.log("phase 3: " + requestId);
				return asyncRedirect(attrs, requestId, {
				});
			}
		} else {
			return {
			};
		}
	}, onBeforeSendHeaders = (requestDetails) => {
		const sendField = function (name, value, include_quotes) {
			const quotes = include_quotes ? "\"" : "";
			return name + "=" + quotes + value + quotes;;
		}
		console.log("-------------------");
		console.log("onBeforeSendHeaders");
		const requestId = requestDetails.requestId;
		const lastResponse = lastResponseGlobal[requestId];
		delete lastResponseGlobal[requestId];
		console.log(pendingRequests);
		console.log(requestDetails);
		const index = pendingRequests.indexOf(requestId);
		if (index > -1) {
			const requestHeaders = requestDetails.requestHeaders;
			let authorization = "SASL";
			let sep = " ";

			if (lastResponse.mech) {
				authorization += sep + sendField("mech", lastResponse.mech, true);
				sep = ","
			}
			if (lastResponse.realm) {
				authorization += sep + sendField("realm", lastResponse.realm, true);
				sep = ","
			}
			if (lastResponse.s2s) {
				authorization += sep + sendField("s2s", lastResponse.s2s, false);
				sep = ","
			}
			if (lastResponse.c2s) {
				authorization += sep + sendField("c2s", lastResponse.c2s, false);
				sep = ","
			}
			if (lastResponse.c2c) {
				c2c[requestId] = lastResponse.c2c;
				console.log("c2c: " + atob(lastResponse.c2c));
			}
			console.log(authorization);
			if (lastResponse.c2s) {
				console.log("c2s: " + saslDataToString(lastResponse.c2s));
			}
			requestDetails.requestHeaders.push(
				{
					name: "Authorization",
					value: authorization
				}
			);
			return { requestHeaders: requestHeaders };
		} else {
			return { };
		}
	}
	;

browser.webRequest.onHeadersReceived.addListener(
        onHeadersReceived,
        { urls: [ target ] },
        [ "blocking", "responseHeaders" ]
        );

browser.webRequest.onBeforeSendHeaders.addListener(
        onBeforeSendHeaders,
        { urls: [ target ] },
        [ "blocking", "requestHeaders" ]
        );

browser.webRequest.onCompleted.addListener(
        completed,
        { urls: [ target ] }
);

browser.webRequest.onErrorOccurred.addListener(
        completed,
        { urls: [ target ] }
);
