const
	target = "<all_urls>"
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
	}, binaryToHex = (binary) => {
		let result = "";
		for (let i = 0; i < binary.length; i++) {
			result += ("00" + binary.charCodeAt(i).toString(16)).slice(-2) + " ";
		}
		return result;
	}, saslDataToString = (str) => {
		const data = atob(str);
		return binaryToHex(data);
	}, onHeadersReceivedPrelude = (requestDetails, handler) => {
		let i;
		const responseHeaders = requestDetails.responseHeaders;
		for (i = 0; i < responseHeaders.length; i++) {
			responseHeaders[responseHeaders[i].name] = responseHeaders[i];
		}
		console.log("------------------------");
		console.log("onHeadersReceivedPrelude");
		console.log(requestDetails);
		const authenticate = responseHeaders["WWW-Authenticate"];
		if (authenticate) {
			const attrs = parseSasl(authenticate.value);
			const requestId = requestDetails.requestId;
			attrs.requestId = requestId;

			console.log("Status code: " + requestDetails.statusCode);
			console.log(attrs);

			if (attrs.mech) {
				console.log("mech: " + attrs.mech);
			}
			if (attrs.s2c) {
				console.log("s2c: " + saslDataToString(attrs.s2c));
			}
			if (attrs.s2s) {
				console.log("s2s: " + atob(attrs.s2s));
			}
			return handler(requestDetails, attrs);
		} else {
			return {
			};
		}
	}, completed = (requestDetails) => {
		console.log("completed: " + requestDetails.requestId);
	}, asyncRedirect = (attrs) => {
		return new Promise((resolve, reject) => {
			const
				portListener = (response) => {
					const
						onBeforeSendHeaders = (requestDetails) => {
							const
								onHeadersReceived1 = (requestDetails1) => {
									console.log("------------------");
									console.log("onHeadersReceived1");
									return onHeadersReceivedPrelude(requestDetails1, (requestDetails1, attrs) => {
										const requestId1 = requestDetails1.requestId;
										if (requestId1 === requestId) {
											browser.webRequest.onHeadersReceived.removeListener(onHeadersReceived1);
											if (response.c2c) {
												console.log("c2c: " + atob(response.c2c));
												attrs.c2c = response.c2c;
											}
											if (requestDetails1.statusCode === 401) {
												console.log("phase 2: " + requestId1);
												attrs.extraInfoSpec = {
													redirectUrl: requestDetails1.url
												};
											} else {
												console.log("phase 3: " + requestId1);
												attrs.extraInfoSpec = {
												};
											}
											return asyncRedirect(attrs);
										}
									});
								}
								;

							console.log("-------------------");
							console.log("onBeforeSendHeaders");
							console.log(requestDetails);
							const requestId = requestDetails.requestId;
							if (requestId === response.requestId) {
								browser.webRequest.onBeforeSendHeaders.removeListener(onBeforeSendHeaders);
								const sendField = function (name, value, include_quotes) {
									const quotes = include_quotes ? "\"" : "";
									return name + "=" + quotes + value + quotes;;
								}
								const requestHeaders = requestDetails.requestHeaders;
								let authorization = "SASL";
								let sep = " ";

								if (response.mech) {
									authorization += sep + sendField("mech", response.mech, true);
									sep = ","
								}
								if (response.realm) {
									authorization += sep + sendField("realm", response.realm, true);
									sep = ","
								}
								if (response.s2s) {
									authorization += sep + sendField("s2s", response.s2s, false);
									sep = ","
								}
								if (response.c2s) {
									authorization += sep + sendField("c2s", response.c2s, false);
									sep = ","
								}
								if (response.c2c) {
									console.log("c2c: " + atob(response.c2c));
								}
								console.log(authorization);
								if (response.c2s) {
									console.log("c2s: " + saslDataToString(response.c2s));
								}
								requestDetails.requestHeaders.push(
									{
										name: "Authorization",
										value: authorization
									}
								);
								browser.webRequest.onHeadersReceived.addListener(
									onHeadersReceived1,
									{ urls: [ target ] },
									[ "blocking", "responseHeaders" ]
								);
								return { requestHeaders: requestHeaders };
							}
						}
						;

					console.log("response " + response.requestId + ": " + JSON.stringify(response));
					console.log("portListener.requestId: " + portListener.requestId);
					port.onMessage.removeListener(portListener);
					console.log("extraInfoSpec: " + JSON.stringify(attrs.extraInfoSpec));
					if (attrs.extraInfoSpec) {
						browser.webRequest.onBeforeSendHeaders.addListener(
							onBeforeSendHeaders,
							{ urls: [ target ] },
							[ "blocking", "requestHeaders" ]
						);
					}
					resolve(response.extraInfoSpec);
				}
				;

			console.log("posting " + attrs.requestId + ": " + JSON.stringify(attrs));
			portListener.requestId = attrs.requestId;
			port.onMessage.addListener(portListener);
			port.postMessage(attrs);
		});
	}, onHeadersReceived = (requestDetails) => {
		console.log("-----------------");
		console.log("onHeadersReceived");
		return onHeadersReceivedPrelude(requestDetails, (requestDetails, attrs) => {
			const
				requestId = requestDetails.requestId
				;

			if (requestDetails.statusCode === 401) {
				if (attrs.mech) {
					attrs.extraInfoSpec = {
						redirectUrl: requestDetails.url
					};
					return asyncRedirect(attrs);
				}
			}
		});
	}	;

browser.webRequest.onHeadersReceived.addListener(
        onHeadersReceived,
        { urls: [ target ] },
        [ "blocking", "responseHeaders" ]
        );

browser.webRequest.onCompleted.addListener(
		completed,
		{ urls: [ target ] }
);

browser.webRequest.onErrorOccurred.addListener(
        completed,
        { urls: [ target ] }
);
