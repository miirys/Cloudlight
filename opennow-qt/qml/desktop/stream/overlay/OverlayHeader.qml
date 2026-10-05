import QtQuick
import OpenNOW

// The lighter bar across the top of the panel. The main page shows the app name with
// feedback, settings and close; a sub-page shows a back arrow, its title and close.
Rectangle {
    id: root
    property string title: ""
    property bool showBack: false
    property bool showActions: false
    signal backRequested()
    signal closeRequested()
    signal settingsRequested()
    signal feedbackRequested()
    color: OverlayStyle.header
    height: OverlayStyle.headerHeight

    component HeaderButton: Item {
        id: button
        property string icon: ""
        property string label: ""
        signal clicked()
        width: OverlayStyle.u(56)
        height: OverlayStyle.u(56)
        anchors.verticalCenter: parent ? parent.verticalCenter : undefined
        Accessible.role: Accessible.Button
        Accessible.name: label
        Rectangle {
            anchors.fill: parent
            radius: width / 2
            color: "#FFFFFF"
            opacity: buttonHover.hovered ? 0.08 : 0
            Behavior on opacity { NumberAnimation { duration: OverlayStyle.fastDuration } }
        }
        OverlayIcon {
            anchors.centerIn: parent
            width: OverlayStyle.u(30)
            height: width
            name: button.icon
            ink: OverlayStyle.headerIcon
        }
        HoverHandler { id: buttonHover }
        TapHandler { onTapped: button.clicked() }
    }

    HeaderButton {
        id: back
        visible: root.showBack
        x: OverlayStyle.iconCenter - width / 2
        icon: "back"
        label: qsTr("Back")
        onClicked: root.backRequested()
    }
    Text {
        x: root.showBack ? OverlayStyle.textInset : OverlayStyle.iconCenter
        width: actions.x - x - OverlayStyle.u(16)
        anchors.verticalCenter: parent.verticalCenter
        text: root.title
        color: OverlayStyle.headerText
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.titleSize
        font.variableAxes: OverlayStyle.strongAxes
        elide: Text.ElideRight
    }
    Row {
        id: actions
        anchors.right: parent.right
        anchors.rightMargin: OverlayStyle.u(17)
        anchors.verticalCenter: parent.verticalCenter
        spacing: OverlayStyle.u(24)
        HeaderButton {
            visible: root.showActions
            objectName: "overlayFeedback"
            icon: "feedback"
            label: qsTr("Send feedback")
            onClicked: root.feedbackRequested()
        }
        HeaderButton {
            visible: root.showActions
            objectName: "overlaySettings"
            icon: "settings"
            label: qsTr("Settings")
            onClicked: root.settingsRequested()
        }
        HeaderButton {
            objectName: "streamMenuClose"
            icon: "close"
            label: qsTr("Resume game")
            onClicked: root.closeRequested()
        }
    }
}
