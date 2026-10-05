import QtQuick
import OpenNOW

QtObject {
    property Component consoleSettings: Component { SettingsScreen { visible: false; selectedSection: 1 } }
    property QtObject client: QtObject {
        property string state: "ready"
        property string lastError: ""
        property var calls: []
        signal responseReceived(string requestId, var result)
        signal requestFailed(string requestId, string code, string message)
        signal eventReceived(string name, var payload)
        function markUiReady() {}
        function logShellDiagnostic(message) {}
        function request(method, params, timeout) {
            const id = "fixture-" + (calls.length + 1)
            calls = calls.concat([{id:id, method:method, params:params}])
            return id
        }
        function cancel(id) { return true }
    }
    function check(ok, message) { if (!ok) throw new Error("Save bandwidth: " + message) }
    function find(item, name) {
        if (item.objectName === name) return item
        for (const child of item.children || []) {
            const found = find(child, name)
            if (found) return found
        }
        return null
    }
    function run(parent) {
        ShellStore.settings = Object.assign({}, ShellStore.settings, {resolution: "1920x1080", fps: 60})
        const control = find(parent, "networkAdjustControl")
        check(control, "desktop exposes the network adjustment choice")
        const current = () => control.options[control.selectedIndex].value
        check(current() === "off", "desktop preference defaults off, so fixed quality stays the wire default")
        ShellStore.settings = Object.assign({}, ShellStore.settings, {saveBandwidth:true, networkAdjust:"latency"})
        const consolePage = consoleSettings.createObject(parent)
        const consoleRow = () => consolePage.settingsModel().find(item => item.key === "saveBandwidth")
        check(consoleRow() && consoleRow().toggle && consoleRow().v === "On"
            && consoleRow().t === "Save bandwidth", "console exposes the same preference")
        for (const choice of ["off", "quality", "latency"]) {
            const index = control.options.findIndex(item => item.value === choice)
            control.selected(index, control.options[index])
            const write = client.calls[client.calls.length - 1]
            check(write.method === "settings.set" && write.params.key === "networkAdjust"
                && write.params.value === choice, "desktop requests the persisted preference")
            client.eventReceived("settings.changed", {key:"networkAdjust", value:choice, changes:{saveBandwidth:choice !== "off"}})
            client.responseReceived(write.id, {key:"networkAdjust", value:choice, changes:{saveBandwidth:choice !== "off"}})
            check(current() === choice && ShellStore.settings.networkAdjust === choice
                && ShellStore.settings.saveBandwidth === (choice !== "off"), "desktop reflects the saved value")
            check(consoleRow().v === (choice !== "off" ? "On" : "Off"), "console reflects desktop changes")
        }
        consolePage.activate(consoleRow())
        let write = client.calls[client.calls.length - 1]
        check(write.method === "settings.set" && write.params.key === "saveBandwidth"
            && write.params.value === false, "console writes the same preference")
        client.eventReceived("settings.changed", {key:"saveBandwidth", value:false, changes:{networkAdjust:"off"}})
        client.responseReceived(write.id, {key:"saveBandwidth", value:false, changes:{networkAdjust:"off"}})
        check(current() === "off", "desktop reflects console changes")
        consolePage.activate(consoleRow())
        write = client.calls[client.calls.length - 1]
        check(write.method === "settings.set" && write.params.key === "saveBandwidth"
            && write.params.value === true, "console writes the preference back on")
        client.eventReceived("settings.changed", {key:"saveBandwidth", value:true, changes:{networkAdjust:"latency"}})
        client.responseReceived(write.id, {key:"saveBandwidth", value:true, changes:{networkAdjust:"latency"}})
        check(current() === "latency" && consoleRow().v === "On",
            "both surfaces agree once the preference is enabled")
        consolePage.destroy()
        return true
    }
}
