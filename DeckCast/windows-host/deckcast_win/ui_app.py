"""PyQt6 Studio Control Center & Multi-Window Floating Viewer for DeckCast on Windows."""

from __future__ import annotations

import asyncio
import threading
from typing import Dict, List, Optional

import numpy as np
from PyQt6.QtCore import QObject, Qt, pyqtSignal
from PyQt6.QtGui import QImage, QKeyEvent, QMouseEvent, QPixmap, QWheelEvent
from PyQt6.QtWidgets import (
    QApplication,
    QComboBox,
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from .controller_client import RemoteControllerClient
from .discovery import MdnsBrowser
from .host_server import WindowsHostServer
from .protocol import DEFAULT_WS_PORT, DiscoveredHost, get_primary_lan_ip

STUDIO_QSS = """
QWidget {
    background-color: #0B0E14;
    color: #F0F6FC;
    font-family: "Segoe UI", sans-serif;
    font-size: 13px;
}
QFrame#Card {
    background-color: #121826;
    border: 1px solid #24324A;
    border-radius: 12px;
}
QLabel#Title {
    font-size: 22px;
    font-weight: 700;
    color: #00E5FF;
}
QLabel#Subtitle {
    font-size: 11px;
    font-weight: 600;
    color: #94A3B8;
    letter-spacing: 1px;
}
QLabel#CardTitle {
    font-size: 14px;
    font-weight: 700;
    color: #F0F6FC;
}
QLabel#Muted {
    color: #94A3B8;
    font-size: 12px;
}
QPushButton {
    background-color: #1E293B;
    color: #F0F6FC;
    border: 1px solid #334155;
    border-radius: 8px;
    padding: 8px 14px;
    font-weight: 600;
}
QPushButton:hover {
    background-color: #283548;
    border-color: #00E5FF;
}
QPushButton:disabled {
    color: #64748B;
    background-color: #141B29;
    border-color: #1E293B;
}
QPushButton#Primary {
    background-color: #00E5FF;
    color: #071318;
    border: none;
}
QPushButton#Primary:hover {
    background-color: #38ECFF;
}
QPushButton#Primary:disabled {
    background-color: #153A44;
    color: #64748B;
}
QPushButton#Danger {
    background-color: #2B1420;
    color: #FF6B81;
    border: 1px solid #5C2237;
}
QPushButton#Danger:hover {
    background-color: #3D192B;
}
QLineEdit, QComboBox {
    background-color: #0B0F19;
    color: #F0F6FC;
    border: 1px solid #2A3B58;
    border-radius: 8px;
    padding: 7px 10px;
}
QLineEdit:focus, QComboBox:focus {
    border-color: #00E5FF;
}
QListWidget {
    background-color: #0B0F19;
    border: 1px solid #24324A;
    border-radius: 8px;
    padding: 4px;
}
QListWidget::item {
    background-color: #161F30;
    border-radius: 6px;
    padding: 8px 10px;
    margin: 3px 0;
}
QListWidget::item:selected {
    background-color: #1C2D46;
    border: 1px solid #00E5FF;
}
"""


class _AsyncBridge(QObject):
    """Dedicated asyncio background thread bridged safely to Qt signals."""

    frame_ready = pyqtSignal(str, object)
    session_state = pyqtSignal(str, str)
    hosts_updated = pyqtSignal(object)
    host_clients = pyqtSignal(int)

    def __init__(self) -> None:
        super().__init__()
        self.loop = asyncio.new_event_loop()
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()

    def _run_loop(self) -> None:
        asyncio.set_event_loop(self.loop)
        self.loop.run_forever()

    def submit(self, coro) -> None:
        asyncio.run_coroutine_threadsafe(coro, self.loop)

    def shutdown(self) -> None:
        self.loop.call_soon_threadsafe(self.loop.stop)
        self._thread.join(timeout=2.0)


class _VideoSurface(QLabel):
    """Aspect-preserving remote display surface translating mouse/wheel/keys to normalized input."""

    def __init__(self, on_pointer, on_key, parent=None) -> None:
        super().__init__(parent)
        self._on_pointer = on_pointer
        self._on_key = on_key
        self._latest_image: Optional[QImage] = None
        self._draw_rect = (0, 0, 1, 1)
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setStyleSheet("background-color: #05070B; color: #94A3B8; border-radius: 8px;")
        self.setText("Connecting to remote stream…")
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self.setMouseTracking(False)

    def update_frame(self, rgb: np.ndarray) -> None:
        h, w, _ = rgb.shape
        img = QImage(rgb.data, w, h, w * 3, QImage.Format.Format_RGB888).copy()
        self._latest_image = img
        self._render_scaled()

    def resizeEvent(self, event) -> None:
        super().resizeEvent(event)
        self._render_scaled()

    def _render_scaled(self) -> None:
        if self._latest_image is None:
            return
        pix = QPixmap.fromImage(self._latest_image).scaled(
            self.size(),
            Qt.AspectRatioMode.KeepAspectRatio,
            Qt.TransformationMode.SmoothTransformation,
        )
        ox = (self.width() - pix.width()) // 2
        oy = (self.height() - pix.height()) // 2
        self._draw_rect = (ox, oy, max(1, pix.width()), max(1, pix.height()))
        self.setPixmap(pix)

    def _to_normalized(self, pos) -> tuple[float, float]:
        ox, oy, dw, dh = self._draw_rect
        nx = max(0.0, min(1.0, (pos.x() - ox) / dw))
        ny = max(0.0, min(1.0, (pos.y() - oy) / dh))
        return nx, ny

    def mousePressEvent(self, event: QMouseEvent) -> None:
        self.setFocus()
        if event.button() == Qt.MouseButton.LeftButton:
            nx, ny = self._to_normalized(event.position())
            self._on_pointer("DOWN", nx, ny, 0.0)

    def mouseMoveEvent(self, event: QMouseEvent) -> None:
        if event.buttons() & Qt.MouseButton.LeftButton:
            nx, ny = self._to_normalized(event.position())
            self._on_pointer("MOVE", nx, ny, 0.0)

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:
        if event.button() == Qt.MouseButton.LeftButton:
            nx, ny = self._to_normalized(event.position())
            self._on_pointer("UP", nx, ny, 0.0)

    def wheelEvent(self, event: QWheelEvent) -> None:
        nx, ny = self._to_normalized(event.position())
        delta = event.angleDelta().y() / 120.0
        self._on_pointer("SCROLL", nx, ny, delta)

    def keyPressEvent(self, event: QKeyEvent) -> None:
        qt_key = event.key()
        if qt_key == Qt.Key.Key_Backspace:
            self._on_key("DOWN", 67, "")
            self._on_key("UP", 67, "")
        elif qt_key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
            self._on_key("DOWN", 66, "")
            self._on_key("UP", 66, "")
        elif qt_key == Qt.Key.Key_Escape:
            self._on_key("DOWN", 4, "")
            self._on_key("UP", 4, "")
        elif event.text():
            self._on_key("DOWN", 0, event.text())
            self._on_key("UP", 0, event.text())


class RemoteViewerWindow(QWidget):
    """Independent floating-capable Windows viewer for controlling an Android/Windows Host."""

    def __init__(self, host: DiscoveredHost, bridge: _AsyncBridge, on_closed) -> None:
        super().__init__()
        self.host = host
        self._bridge = bridge
        self._on_closed = on_closed
        self._pinned = False

        self.setWindowTitle(f"DeckCast — {host.device_name}")
        self.resize(440, 780)
        self.setStyleSheet(STUDIO_QSS)

        self.client = RemoteControllerClient(
            host_address=host.host_address,
            ws_port=host.ws_port,
            on_frame=lambda rgb: self._bridge.frame_ready.emit(self.client.session_id, rgb),
            on_state_changed=lambda st: self._bridge.session_state.emit(self.client.session_id, st),
        )

        root = QVBoxLayout(self)
        root.setContentsMargins(10, 10, 10, 10)
        root.setSpacing(8)

        bar = QHBoxLayout()
        self.lbl_status = QLabel(f"● {host.device_name} — connecting…")
        self.lbl_status.setStyleSheet("color: #FFB020; font-weight: 600;")
        self.btn_back = QPushButton("Back")
        self.btn_home = QPushButton("Home")
        self.btn_recents = QPushButton("Recents")
        self.btn_pin = QPushButton("Pin Top")
        for b in (self.btn_back, self.btn_home, self.btn_recents, self.btn_pin):
            b.setStyleSheet("padding: 5px 10px; font-size: 12px;")

        bar.addWidget(self.lbl_status, 1)
        bar.addWidget(self.btn_back)
        bar.addWidget(self.btn_home)
        bar.addWidget(self.btn_recents)
        bar.addWidget(self.btn_pin)
        root.addLayout(bar)

        self.surface = _VideoSurface(
            on_pointer=lambda a, x, y, s: self.client.send_pointer(a, x, y, s),
            on_key=lambda a, k, t: self.client.send_key(a, k, t),
        )
        root.addWidget(self.surface, 1)

        self.btn_back.clicked.connect(lambda: (self.client.send_key("DOWN", 4), self.client.send_key("UP", 4)))
        self.btn_home.clicked.connect(lambda: (self.client.send_key("DOWN", 3), self.client.send_key("UP", 3)))
        self.btn_recents.clicked.connect(lambda: (self.client.send_key("DOWN", 187), self.client.send_key("UP", 187)))
        self.btn_pin.clicked.connect(self._toggle_pin)

        self.client.start(self._bridge.loop)

    def _toggle_pin(self) -> None:
        self._pinned = not self._pinned
        self.setWindowFlag(Qt.WindowType.WindowStaysOnTopHint, self._pinned)
        self.btn_pin.setText("Pinned ✓" if self._pinned else "Pin Top")
        self.show()

    def update_status(self, state: str) -> None:
        color = {
            "connected": "#2EF2A0",
            "connecting": "#FFB020",
        }.get(state.lower(), "#FF6B81")
        self.lbl_status.setStyleSheet(f"color: {color}; font-weight: 600;")
        self.lbl_status.setText(f"● {self.host.device_name} — {state}")

    def closeEvent(self, event) -> None:
        self._bridge.submit(self.client.close())
        self._on_closed(self.client.session_id)
        super().closeEvent(event)


class DeckCastControlWindow(QMainWindow):
    """Main Windows Control Center: start Windows->Android Host or open Android->Windows Viewers."""

    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("DeckCast Studio")
        self.resize(540, 620)
        self.setStyleSheet(STUDIO_QSS)

        self._bridge = _AsyncBridge()
        self._host_server: Optional[WindowsHostServer] = None
        self._browser: Optional[MdnsBrowser] = None
        self._viewers: Dict[str, RemoteViewerWindow] = {}
        self._discovered: List[DiscoveredHost] = []

        self._bridge.frame_ready.connect(self._on_frame)
        self._bridge.session_state.connect(self._on_session_state)
        self._bridge.hosts_updated.connect(self._on_hosts_updated)
        self._bridge.host_clients.connect(self._on_host_clients)

        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)
        layout.setContentsMargins(18, 18, 18, 18)
        layout.setSpacing(14)

        # Header
        header_row = QHBoxLayout()
        title_col = QVBoxLayout()
        title_col.setSpacing(2)
        title = QLabel("DeckCast")
        title.setObjectName("Title")
        subtitle = QLabel("OFFLINE LAN MULTI-SCREEN STUDIO")
        subtitle.setObjectName("Subtitle")
        title_col.addWidget(title)
        title_col.addWidget(subtitle)
        header_row.addLayout(title_col, 1)

        self.lbl_lan_pill = QLabel(f"LAN {get_primary_lan_ip()}:{DEFAULT_WS_PORT}")
        self.lbl_lan_pill.setStyleSheet(
            "background: #132533; color: #00E5FF; border: 1px solid #1F4D63; "
            "border-radius: 12px; padding: 5px 12px; font-weight: 600; font-size: 12px;"
        )
        header_row.addWidget(self.lbl_lan_pill)
        layout.addLayout(header_row)

        # Windows -> Android Host Card
        host_card = QFrame()
        host_card.setObjectName("Card")
        hc = QVBoxLayout(host_card)
        hc.setContentsMargins(16, 14, 16, 14)
        hc.setSpacing(10)

        hc_top = QHBoxLayout()
        hc_title = QLabel("Host Mode — Share This Windows Screen")
        hc_title.setObjectName("CardTitle")
        self.lbl_host_badge = QLabel("● IDLE")
        self.lbl_host_badge.setStyleSheet("color: #94A3B8; font-weight: 700; font-size: 11px;")
        hc_top.addWidget(hc_title, 1)
        hc_top.addWidget(self.lbl_host_badge)
        hc.addLayout(hc_top)

        self.lbl_host_info = QLabel("Stream desktop video + receive touch/keys from Android")
        self.lbl_host_info.setObjectName("Muted")
        hc.addWidget(self.lbl_host_info)

        hc_controls = QHBoxLayout()
        self.cmb_quality = QComboBox()
        self.cmb_quality.addItems(["720p @ 60fps (Low Latency)", "1080p @ 30fps (Sharp)", "720p @ 30fps (Battery)"])
        self.btn_start_host = QPushButton("Start Host")
        self.btn_start_host.setObjectName("Primary")
        self.btn_stop_host = QPushButton("Stop")
        self.btn_stop_host.setObjectName("Danger")
        self.btn_stop_host.setEnabled(False)
        hc_controls.addWidget(self.cmb_quality, 1)
        hc_controls.addWidget(self.btn_start_host)
        hc_controls.addWidget(self.btn_stop_host)
        hc.addLayout(hc_controls)
        layout.addWidget(host_card)

        # Android -> Windows Viewer Card
        ctrl_card = QFrame()
        ctrl_card.setObjectName("Card")
        cc = QVBoxLayout(ctrl_card)
        cc.setContentsMargins(16, 14, 16, 14)
        cc.setSpacing(10)

        cc_top = QHBoxLayout()
        cc_title = QLabel("Controller Mode — View & Control Remote Devices")
        cc_title.setObjectName("CardTitle")
        self.btn_scan = QPushButton("Rescan LAN")
        cc_top.addWidget(cc_title, 1)
        cc_top.addWidget(self.btn_scan)
        cc.addLayout(cc_top)

        self.list_hosts = QListWidget()
        cc.addWidget(self.list_hosts, 1)

        hint = QLabel("Double-click a discovered host above, or enter IP:Port manually:")
        hint.setObjectName("Muted")
        cc.addWidget(hint)

        manual_row = QHBoxLayout()
        self.et_manual = QLineEdit()
        self.et_manual.setPlaceholderText("192.168.1.42:9443")
        self.btn_connect = QPushButton("Open Window")
        self.btn_connect.setObjectName("Primary")
        manual_row.addWidget(self.et_manual, 1)
        manual_row.addWidget(self.btn_connect)
        cc.addLayout(manual_row)
        layout.addWidget(ctrl_card, 1)

        self.btn_start_host.clicked.connect(self._start_host)
        self.btn_stop_host.clicked.connect(self._stop_host)
        self.btn_scan.clicked.connect(self._restart_scan)
        self.btn_connect.clicked.connect(self._connect_manual)
        self.list_hosts.itemDoubleClicked.connect(self._connect_list_item)

        self._restart_scan()

    def _selected_preset(self) -> tuple[int, int, int]:
        idx = self.cmb_quality.currentIndex()
        if idx == 1:
            return (1920, 1080, 30)
        if idx == 2:
            return (1280, 720, 30)
        return (1280, 720, 60)

    def _start_host(self) -> None:
        if self._host_server is not None:
            return
        w, h, fps = self._selected_preset()
        self._host_server = WindowsHostServer(
            port=DEFAULT_WS_PORT,
            max_width=w,
            max_height=h,
            fps=fps,
            on_clients_changed=lambda count: self._bridge.host_clients.emit(count),
        )
        self._bridge.submit(self._host_server.start())
        self.lbl_host_badge.setText("● LIVE")
        self.lbl_host_badge.setStyleSheet("color: #2EF2A0; font-weight: 700; font-size: 11px;")
        self.lbl_host_info.setText(
            f"ws://{get_primary_lan_ip()}:{DEFAULT_WS_PORT}/signal ({w}×{h} @ {fps}fps) • Clients: 0"
        )
        self.btn_start_host.setEnabled(False)
        self.btn_stop_host.setEnabled(True)
        self.cmb_quality.setEnabled(False)

    def _stop_host(self) -> None:
        if self._host_server is None:
            return
        srv = self._host_server
        self._host_server = None
        self._bridge.submit(srv.stop())
        self.lbl_host_badge.setText("● IDLE")
        self.lbl_host_badge.setStyleSheet("color: #94A3B8; font-weight: 700; font-size: 11px;")
        self.lbl_host_info.setText("Stream desktop video + receive touch/keys from Android")
        self.btn_start_host.setEnabled(True)
        self.btn_stop_host.setEnabled(False)
        self.cmb_quality.setEnabled(True)

    def _restart_scan(self) -> None:
        if self._browser is not None:
            self._browser.stop()
        self._browser = MdnsBrowser(lambda hosts: self._bridge.hosts_updated.emit(hosts))
        self._browser.start()

    def _connect_manual(self) -> None:
        raw = self.et_manual.text().strip()
        if not raw:
            return
        parts = raw.split(":")
        ip = parts[0].strip()
        port = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else DEFAULT_WS_PORT
        self._open_viewer(DiscoveredHost(f"Manual-{ip}", ip, "android", ip, port))

    def _connect_list_item(self, item: QListWidgetItem) -> None:
        idx = self.list_hosts.row(item)
        if 0 <= idx < len(self._discovered):
            self._open_viewer(self._discovered[idx])

    def _open_viewer(self, host: DiscoveredHost) -> None:
        viewer = RemoteViewerWindow(
            host=host,
            bridge=self._bridge,
            on_closed=lambda sid: self._viewers.pop(sid, None),
        )
        self._viewers[viewer.client.session_id] = viewer
        viewer.show()

    def _on_hosts_updated(self, hosts: List[DiscoveredHost]) -> None:
        self._discovered = hosts
        self.list_hosts.clear()
        for h in hosts:
            self.list_hosts.addItem(f"{h.device_name}   [{h.os_type.upper()}]   {h.host_address}:{h.wsPort if hasattr(h, 'wsPort') else h.ws_port}")

    def _on_frame(self, session_id: str, rgb: np.ndarray) -> None:
        viewer = self._viewers.get(session_id)
        if viewer is not None:
            viewer.surface.update_frame(rgb)

    def _on_session_state(self, session_id: str, state: str) -> None:
        viewer = self._viewers.get(session_id)
        if viewer is not None:
            viewer.update_status(state)

    def _on_host_clients(self, count: int) -> None:
        if self._host_server is not None:
            w, h, fps = self._selected_preset()
            self.lbl_host_info.setText(
                f"ws://{get_primary_lan_ip()}:{DEFAULT_WS_PORT}/signal ({w}×{h} @ {fps}fps) • Clients: {count}"
            )

    def closeEvent(self, event) -> None:
        if self._browser is not None:
            self._browser.stop()
        for v in list(self._viewers.values()):
            v.close()
        if self._host_server is not None:
            self._bridge.submit(self._host_server.stop())
        self._bridge.shutdown()
        super().closeEvent(event)


def run_gui() -> None:
    app = QApplication([])
    win = DeckCastControlWindow()
    win.show()
    app.exec()
