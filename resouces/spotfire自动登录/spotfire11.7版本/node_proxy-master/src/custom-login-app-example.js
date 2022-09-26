import TSSCustomLoginApi from "/js-api/custom-login-api.js";
const customLoginApi = new TSSCustomLoginApi();

customLoginApi.getServerManifest().then((serverInfo) => {
  // Do not show login page if user is already logged in.
  if (!serverInfo.showLoginPage) {
    customLoginApi.redirectToTargetUrl();
  }

  // Remember
  const rememberGroup = document.getElementById("remember-group");
  if (rememberGroup && serverInfo && serverInfo.allowSaveInformation) {
    rememberGroup.classList.remove("hidden");
  }

  // Web Authentication Providers
  addWebAuthProviders(serverInfo.webAuthProviders);
});

function doLogin() {
  const username = document.getElementById("username").value;
  const password = document.getElementById("password").value;
  const remember = document.getElementById("remember").checked;
  const eula = document.getElementById("eula").checked;

  // Optional payload that can be passed to the server. Key prefix must start
  // with sf_custom_login_, otherwise they will not be sent in the request.
  const optionalPayload = {
    sf_custom_login_extra_string: "My own custom login value",
    sf_custom_login_extra_boolean: true,
    invalid_key: "keys need to start with sf_custom_login_",
	libaryId: "256600f1-f50a-4276-9848-0b415ecd1721"
  };

  if (!username || !password) {
    showErrorMessage("Please specify both username and password!");
    return;
  }

  if (!eula) {
    showErrorMessage("You must accept the terms of service!");
    return;
  }

  removeErrorMessage();
  customLoginApi.login(username, password, remember, loginSuccess, loginError, optionalPayload);
}

function loginSuccess(libaryId) {
  removeErrorMessage();
  var targetUrl = "/spotfire/wp/OpenAnalysis?file="+libaryId;
  customLoginApi.redirectToTargetUrl(targetUrl);
}

function loginError(response) {
  if (response.status === 401) {
    showErrorMessage("Wrong username or password.");
  } else if (response.status === 0) {
    // Getting a status of 0 when server could not be contacted.
    showErrorMessage("Server could not be contacted");
  } else {
    // If anything else then unauthorizd, display error message.
    showErrorMessage("Internal Server Error");
  }
}

function showErrorMessage(message) {
  if (!message) {
    return;
  }

  const errorElement = document.getElementById("error");
  if (!errorElement) {
    return;
  }

  errorElement.innerText = message;
  errorElement.classList.remove("hidden");
}

function removeErrorMessage() {
  const errorElement = document.getElementById("error");
  if (!errorElement) {
    return;
  }
  errorElement.classList.add("hidden");
}

function addWebAuthProviders(webAuthProviders) {
  if (!webAuthProviders || webAuthProviders.length === 0) {
    return;
  }

  const ul = document.getElementById("wap-list");
  webAuthProviders.forEach((wap) => {
    const li = document.createElement("li");
    const button = document.createElement("button");
    button.textContent = "Login with " + wap.label;
    button.setAttribute("data-provider", wap.providerName);
    button.onclick = function (event) {
      const provider = event.target.dataset.provider;
      if (provider) {
        customLoginApi.redirectToWebAuthProvider(provider);
      }
    };

    li.appendChild(button);
    ul.appendChild(li);
  });

  // Show
  document.getElementById("wap-container").classList.remove("hidden");
}
//清空cookie
function clearCookie() {
			var keys = document.cookie.match(/[^ =;]+(?=\=)/g);
			if (keys) {
				for (var i = keys.length; i--;) {
					document.cookie = keys[i] + '=0;path=/;expires=' + new Date(0).toUTCString();//清除当前域名下的,例如：m.kevis.com
					document.cookie = keys[i] + '=0;path=/;domain=' + document.domain + ';expires=' + new Date(0).toUTCString();//清除当前域名下的，例如 .m.kevis.com
					document.cookie = keys[i] + '=0;path=/;domain=kevis.com;expires=' + new Date(0).toUTCString();//清除一级域名下的或指定的，例如 .kevis.com
				}
			}
			console.log('已清除');
		}

window.doLogin = doLogin;
window.onload = function(){
         //清楚cookie缓存
	clearCookie();
	var params = new URLSearchParams(window.location.search);
	var username = params.get("username");
	var password = params.get("password");
	const optionalPayload = {
	  sf_custom_login_extra_string: "My own custom login value",
	  sf_custom_login_extra_boolean: true,
	  invalid_key: "keys need to start with sf_custom_login_",
	   libaryId: params.get("libaryId")
	};
	if(username){
		customLoginApi.login(username, password, remember, loginSuccess, loginError, optionalPayload);
	}
	
}