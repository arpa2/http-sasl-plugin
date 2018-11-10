let lastResponse;

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
	}, asyncRedirect = (attrs, obj) => {
		return new Promise((resolve, reject) => {
			const
				portListener = (response) => {
					console.log("s2s: " + response.s2s);
					lastResponse = response;
					resolve(obj);
					port.onMessage.removeListener(portListener);
				}
				;

			port.onMessage.addListener(portListener);
			port.postMessage(attrs);
		});
	}, onHeadersReceived = (requestDetails) => {
		let i;
		const responseHeaders = requestDetails.responseHeaders;
		for (i = 0; i < responseHeaders.length; i++) {
			responseHeaders[responseHeaders[i].name] = responseHeaders[i];
		}
		console.log(requestDetails);
		if (requestDetails.statusCode = 401) {
			if (responseHeaders["WWW-Authenticate"]) {
				// If we have seen this request before,
				// then assume our credentials were bad,
				// and give up.
				const authenticate = responseHeaders["WWW-Authenticate"].value;
				const attrs = parseSasl(authenticate);
				console.log(attrs);
				if (attrs.s2c) {
					console.log("s2c: " + atob(attrs.s2c));
				}
				
				if (pendingRequests.indexOf(requestDetails.requestId) != -1) {
	/*			
					console.log("bad credentials for: " + requestDetails.requestId);
					return {
						cancel: true
					};
	*/			
					console.log("phase 2: " + requestDetails.requestId);
					return asyncRedirect(attrs, {
						redirectUrl: requestDetails.url
					});
				} else {
					pendingRequests.push(requestDetails.requestId);
					console.log("phase 1: " + requestDetails.requestId);
					return asyncRedirect(attrs, {
						redirectUrl: requestDetails.url
					});
				}
				
			} else {
				console.log("Reset sasl-client");
				return {
				};
			}
		} else {
			return {
			};
		}
	}, onBeforeSendHeaders = (requestDetails) => {
		const sendField = function (name, value, include_quotes) {
			let result = "";
			if (value) {
				const quotes = include_quotes ? "\"" : "";
				result = name + "=" + quotes + value + quotes;
			}
			return result;
		}
		console.log(pendingRequests);
		console.log(requestDetails);
		const index = pendingRequests.indexOf(requestDetails.requestId);
		if (index > -1) {
			const requestHeaders = requestDetails.requestHeaders;
			const authorization = 
				"SASL" +
				sendField(" mech", "DIGEST-MD5", true) +
				sendField(",realm", lastResponse.realm, true)  +
				sendField(",s2s", lastResponse.s2s, false) +
				sendField(",c2s", lastResponse.c2s, false)
				;
			console.log(authorization);
			if (lastResponse.c2s) {
				console.log("c2s: " + atob(lastResponse.c2s));
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

/* Old stuff
function parseSasl(sasl) {
	const rc = {};
	if (sasl.slice(0, 4) === "SASL") {
		const pos = 4;
		const c;
		while (true) {
			while ((c = sasl.charAt(pos)) === ' ') {
				pos++;
			}
			if (c === "") {
				break;
			}
			const name = "";
			while ((c = sasl.charAt(pos)) !== '=') {
				name += c;
				pos++;
			}
			c = sasl.charAt(++pos);
			const delim;
			if (c === "\"") {
				delim = "\"";
				pos++;
				} else {
				delim = " ";
			}
			const value = "";
			while ((c = sasl.charAt(pos)) !== delim && c !== "") {
				value += c;
				pos++;
			}
			if (c === "\"" && delim === "\"") {
				pos++;
			}
			rc[name] = value;
		}
	}
	return rc;
}

// Does not work with SASL WWW-Authenticate header
 browser.webRequest.onAuthRequired.addListener(
 provideCredentialsAsync,
 {urls: [ target ]},
 ["blocking"]
 );
*/

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
