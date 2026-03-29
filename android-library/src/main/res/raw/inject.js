inject : {
    function interceptor(e) {
        var form = e ? e.target : this;
        var aa = [];
        for (i = 0; i < form.elements.length; i++) {
            var name = form.elements[i].name;
            var value = form.elements[i].value;
            aa.push({"name" : name, "value" : value});
        }
        interception.customSubmit(
                form.attributes['method'] === undefined ? null
                        : form.attributes['method'].nodeValue,
                form.attributes['action'] === undefined ? null
                        : form.attributes['action'].nodeValue,
                form.attributes['enctype'] === undefined ? null
                        : form.attributes['enctype'].nodeValue,
                JSON.stringify({"form" : aa}));
    }

    function DefineProperty(obj, name) {
        var val = null;
        var prop = {}
        function getter() {
            return val;
        }
        function setter(v) {
            val = v;
        }
        Object.defineProperty(obj, name, {
            get: getter,
            set: setter,
            configurable: true
        });
    }

    // 1) HTML
    window.addEventListener('submit', function(e) {
        interceptor(e);
    }, true);

    // 2) HTMLFormElement.prototype.submit
    HTMLFormElement.prototype.submit = interceptor;

    // 3) XMLHttpRequest.prototype.send
    XMLHttpRequest.prototype.open = function(method, url, async, user, pass) {
        this.params = {
          "method" : method === undefined ? null : method,
          "url" : url === undefined ? null : url,
          "async" : async === undefined ? null : async,
          "user" : user === undefined ? null : user,
          "password" : pass === undefined ? null : pass
        };
        this.header = {};
        this.setRequestHeader = function(name, value) {
            this.header[name] = value;
        }
        DefineProperty(this, 'response');
        DefineProperty(this, 'responseText');
        DefineProperty(this, 'responseURL');
        DefineProperty(this, 'responseXML');
        DefineProperty(this, 'status');
        DefineProperty(this, 'statusText');
        DefineProperty(this, 'readyState');
        this.readyState = 1; // OPENED
    };

    XMLHttpRequest.prototype.send = function(form) {
        var params = this.params;
        var response = interception.customAjax(params.method, params.url, params.user, params.password, JSON.stringify(this.header), form);
        this.response = response;
        this.responseText = response;
        this.responseURL = params.url;
        this.responseXML = response;
        this.readyState = 4; // DONE
        if (response == null) {
            this.status = 500;
            this.statusText = "Internal Server Error";
        } else {
            this.status = 200;
            this.statusText = "OK";
        }
        this.onreadystatechange && this.onreadystatechange();
    }
}
