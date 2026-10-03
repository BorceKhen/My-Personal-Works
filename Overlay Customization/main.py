import ctypes
import json
import math
import os
import sys
from PyQt6.QtCore import QPointF, QRectF, QTimer, Qt
from PyQt6.QtGui import (
    QBrush,
    QColor,
    QIcon,
    QMovie,
    QPainter,
    QPen,
    QPixmap,
    QPolygonF,
)
from PyQt6.QtWidgets import (
    QApplication,
    QCheckBox,
    QFileDialog,
    QGraphicsEllipseItem,
    QGraphicsItem,
    QGraphicsPixmapItem,
    QGraphicsPolygonItem,
    QGraphicsRectItem,
    QGraphicsScene,
    QGraphicsView,
    QHBoxLayout,
    QLabel,
    QMessageBox,
    QPushButton,
    QScrollArea,
    QSizePolicy,
    QStyleOption,
    QVBoxLayout,
    QWidget,
)


def resource_path(relative_path: str) -> str:
    """Gets absolute path to resource, checking script directory first, then CWD."""
    if hasattr(sys, "_MEIPASS"):
        return os.path.join(sys._MEIPASS, relative_path)
    script_dir = os.path.dirname(os.path.abspath(__file__))
    local_path = os.path.join(script_dir, relative_path)
    if os.path.exists(local_path):
        return local_path
    cwd_path = os.path.join(os.path.abspath("."), relative_path)
    if os.path.exists(cwd_path):
        return cwd_path
    return local_path


def get_config_path() -> str:
    """Returns a writable path for saving multi-slot layout presets."""
    if getattr(sys, "frozen", False):
        base_dir = os.path.dirname(sys.executable)
    else:
        base_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(base_dir, "layout_presets.json")


# --- Floating Item Classes ---

class FloatingSticker(QGraphicsPixmapItem):
    """Draggable and floating sticker supporting static images and GIFs."""

    def __init__(self, x: float, y: float, name: str = "Sticker", file_path: str = None):
        super().__init__()
        self.base_x = x
        self.base_y = y
        self.time_offset = (x + y) * 0.01
        self.is_dragging = False
        self.is_animated = True
        self.item_name = name
        self.file_path = file_path
        self.item_type = "image"
        self.movie = None

        self.setPos(x, y)
        self.setFlag(QGraphicsItem.GraphicsItemFlag.ItemIsMovable, True)
        self.setFlag(QGraphicsItem.GraphicsItemFlag.ItemIsSelectable, True)
        self.setFlag(QGraphicsItem.GraphicsItemFlag.ItemSendsGeometryChanges, True)

    def load_media(self, file_path: str) -> bool:
        if not file_path or not os.path.exists(file_path):
            return False

        self.file_path = file_path
        if file_path.lower().endswith(".gif"):
            self.movie = QMovie(file_path)
            if self.movie.isValid():
                self.movie.frameChanged.connect(self._on_gif_frame_changed)
                self.movie.start()
                return True
            return False
        else:
            pixmap = QPixmap(file_path)
            if not pixmap.isNull():
                if pixmap.width() > 250 or pixmap.height() > 250:
                    pixmap = pixmap.scaled(
                        200,
                        200,
                        Qt.AspectRatioMode.KeepAspectRatio,
                        Qt.TransformationMode.SmoothTransformation,
                    )
                self.setPixmap(pixmap)
                return True
            return False

    def _on_gif_frame_changed(self):
        if self.movie:
            pixmap = self.movie.currentPixmap()
            if pixmap.width() > 250 or pixmap.height() > 250:
                pixmap = pixmap.scaled(
                    200,
                    200,
                    Qt.AspectRatioMode.KeepAspectRatio,
                    Qt.TransformationMode.SmoothTransformation,
                )
            self.setPixmap(pixmap)

    def mousePressEvent(self, event):
        self.is_dragging = True
        super().mousePressEvent(event)

    def mouseReleaseEvent(self, event):
        self.is_dragging = False
        self.base_x = self.x()
        self.base_y = self.y()
        self.setSelected(False)
        super().mouseReleaseEvent(event)

    def set_animation_active(self, active: bool):
        self.is_animated = active

    def update_float_animation(self, tick: float):
        if not self.is_dragging:
            if self.is_animated:
                amplitude = 12.0
                speed = 0.05
                offset_y = math.sin((tick * speed) + self.time_offset) * amplitude
                self.setPos(self.base_x, self.base_y + offset_y)
            else:
                self.setPos(self.base_x, self.base_y)

    def remove_from_scene(self, scene: QGraphicsScene):
        if self.movie:
            self.movie.stop()
            self.movie = None
        scene.removeItem(self)

    def to_dict(self):
        return {
            "type": "image",
            "name": self.item_name,
            "x": self.x(),
            "y": self.y(),
            "file_path": self.file_path,
            "is_animated": self.is_animated,
        }


class FloatingShapeItem:
    """Generic floating shape wrapper."""

    def __init__(self, item: QGraphicsItem, x: float, y: float, name: str = "Shape", shape_type: str = "square"):
        self.inner_item = item
        self.base_x = x
        self.base_y = y
        self.time_offset = (x + y) * 0.01
        self.is_dragging = False
        self.is_animated = True
        self.item_name = name
        self.shape_type = shape_type
        self.item_type = "shape"

        self.inner_item.setPos(x, y)
        self.inner_item.setFlag(QGraphicsItem.GraphicsItemFlag.ItemIsMovable, True)
        self.inner_item.setFlag(QGraphicsItem.GraphicsItemFlag.ItemIsSelectable, True)

        orig_release = self.inner_item.mouseReleaseEvent
        orig_press = self.inner_item.mousePressEvent

        def custom_press(event):
            self.is_dragging = True
            orig_press(event)

        def custom_release(event):
            self.is_dragging = False
            self.base_x = self.inner_item.x()
            self.base_y = self.inner_item.y()
            self.inner_item.setSelected(False)
            orig_release(event)

        self.inner_item.mousePressEvent = custom_press
        self.inner_item.mouseReleaseEvent = custom_release

    def x(self):
        return self.inner_item.x()

    def y(self):
        return self.inner_item.y()

    def set_animation_active(self, active: bool):
        self.is_animated = active

    def update_float_animation(self, tick: float):
        if not self.is_dragging:
            if self.is_animated:
                amplitude = 10.0
                speed = 0.04
                offset_y = math.sin((tick * speed) + self.time_offset) * amplitude
                self.inner_item.setPos(self.base_x, self.base_y + offset_y)
            else:
                self.inner_item.setPos(self.base_x, self.base_y)

    def remove_from_scene(self, scene: QGraphicsScene):
        scene.removeItem(self.inner_item)

    def to_dict(self):
        return {
            "type": "shape",
            "name": self.item_name,
            "shape_type": self.shape_type,
            "x": self.x(),
            "y": self.y(),
            "is_animated": self.is_animated,
        }


# --- Fullscreen Desktop Overlay ---

class DesktopOverlay(QGraphicsView):
    def __init__(self):
        super().__init__()

        self.scene = QGraphicsScene(self)
        self.setScene(self.scene)

        self.viewport().setAttribute(
            Qt.WidgetAttribute.WA_TranslucentBackground, True
        )
        self.setBackgroundBrush(QColor(0, 0, 0, 0))

        self.setWindowFlags(
            Qt.WindowType.FramelessWindowHint
            | Qt.WindowType.WindowStaysOnTopHint
            | Qt.WindowType.SubWindow
        )
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        self.setStyleSheet("background: transparent;")

        self.setFrameStyle(0)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)

        screen = QApplication.primaryScreen().geometry()
        self.setGeometry(screen)
        self.scene.setSceneRect(
            0, 0, float(screen.width()), float(screen.height())
        )

        self.items_list = []
        self.item_counter = 1

        self.tick_count = 0
        self.anim_timer = QTimer(self)
        self.anim_timer.timeout.connect(self._on_anim_tick)

    def update_timer_state(self):
        should_run = any(getattr(item, "is_animated", False) for item in self.items_list)
        if should_run and not self.anim_timer.isActive():
            self.anim_timer.start(16)
        elif not should_run and self.anim_timer.isActive():
            self.anim_timer.stop()

    def set_click_through(self, enabled: bool):
        self.setWindowFlag(Qt.WindowType.WindowTransparentForInput, enabled)
        self.show()

    def _on_anim_tick(self):
        self.tick_count += 1
        for item in self.items_list:
            if hasattr(item, "update_float_animation"):
                item.update_float_animation(self.tick_count)

    def add_image_sticker(self, file_path: str, x: float = None, y: float = None, is_animated: bool = True, custom_name: str = None):
        cx = x if x is not None else self.width() / 2
        cy = y if y is not None else self.height() / 2

        filename = os.path.basename(file_path)
        name = custom_name if custom_name else f"#{self.item_counter} {filename[:10]}"

        sticker = FloatingSticker(cx, cy, name=name, file_path=file_path)
        if not sticker.load_media(file_path):
            return None

        sticker.set_animation_active(is_animated)

        if not custom_name:
            self.item_counter += 1

        self.scene.addItem(sticker)
        self.items_list.append(sticker)
        self.update_timer_state()
        return sticker

    def add_shape_sticker(self, shape_type: str, x: float = None, y: float = None, is_animated: bool = True, custom_name: str = None):
        cx = x if x is not None else self.width() / 2
        cy = y if y is not None else self.height() / 2

        color = QColor(0, 229, 255, 180)
        brush = QBrush(color)
        pen = QPen(Qt.PenStyle.NoPen)

        name = custom_name if custom_name else f"#{self.item_counter} {shape_type.capitalize()}"

        if shape_type == "square":
            item = QGraphicsRectItem(QRectF(0, 0, 80, 80))
            item.setBrush(brush)
            item.setPen(pen)

        elif shape_type == "circle":
            item = QGraphicsEllipseItem(QRectF(0, 0, 80, 80))
            item.setBrush(brush)
            item.setPen(pen)

        elif shape_type == "triangle":
            poly = QPolygonF([QPointF(40, 0), QPointF(0, 80), QPointF(80, 80)])
            item = QGraphicsPolygonItem(poly)
            item.setBrush(brush)
            item.setPen(pen)

        elif shape_type == "star":
            points = []
            for i in range(10):
                r = 40 if i % 2 == 0 else 18
                angle = i * math.pi / 5 - math.pi / 2
                points.append(
                    QPointF(40 + r * math.cos(angle), 40 + r * math.sin(angle))
                )
            item = QGraphicsPolygonItem(QPolygonF(points))
            item.setBrush(QBrush(QColor(255, 215, 0, 220)))
            item.setPen(pen)

        else:
            return None

        if not custom_name:
            self.item_counter += 1

        self.scene.addItem(item)
        wrapper = FloatingShapeItem(item, cx, cy, name=name, shape_type=shape_type)
        wrapper.set_animation_active(is_animated)
        self.items_list.append(wrapper)
        self.update_timer_state()
        return wrapper

    def remove_item(self, item):
        if item in self.items_list:
            self.items_list.remove(item)
            if hasattr(item, "remove_from_scene"):
                item.remove_from_scene(self.scene)
            elif isinstance(item, QGraphicsItem):
                self.scene.removeItem(item)
            self.update_timer_state()

    def clear_all_stickers(self):
        for item in self.items_list:
            if hasattr(item, "remove_from_scene"):
                item.remove_from_scene(self.scene)
            elif isinstance(item, FloatingSticker) and item.movie:
                item.movie.stop()
        self.scene.clear()
        self.items_list.clear()
        self.update_timer_state()


# --- Control Panel UI ---

class ControlPanel(QWidget):
    def __init__(self, overlay: DesktopOverlay):
        super().__init__(parent=None)
        self.overlay = overlay
        self.presets_data = {}  # Stores slots 1-5 layout data

        self.setWindowTitle("Overlay Controller")

        icon_path = resource_path("icon.png")
        if os.path.exists(icon_path):
            self.setWindowIcon(QIcon(icon_path))

        self.setWindowFlags(
            Qt.WindowType.Window
            | Qt.WindowType.WindowStaysOnTopHint
            | Qt.WindowType.WindowMinimizeButtonHint
            | Qt.WindowType.WindowCloseButtonHint
        )

        css_file_path = resource_path("style.css")
        if os.path.exists(css_file_path):
            with open(css_file_path, "r", encoding="utf-8") as f:
                self.setStyleSheet(f.read())

        main_layout = QHBoxLayout()
        main_layout.setContentsMargins(16, 16, 16, 16)
        main_layout.setSpacing(14)

        # 0. Left Presets Sidebar (Up to 5 Slots)
        self.preset_sidebar = QWidget()
        self.preset_sidebar.setObjectName("preset_sidebar_container")
        preset_sidebar_layout = QVBoxLayout()
        preset_sidebar_layout.setContentsMargins(0, 0, 0, 0)
        preset_sidebar_layout.setSpacing(6)

        title_presets = QLabel("Presets (1-5)")
        preset_sidebar_layout.addWidget(title_presets)

        self.slot_buttons = {}
        for slot_num in range(1, 6):
            row_w = QWidget()
            row_l = QHBoxLayout()
            row_l.setContentsMargins(0, 0, 0, 0)
            row_l.setSpacing(4)

            btn_slot = QPushButton(f"Slot #{slot_num} [Empty]")
            btn_slot.setProperty("class", "slot_btn")
            btn_slot.clicked.connect(lambda _, s=slot_num: self.load_slot_preset(s))

            btn_save = QPushButton("S")
            btn_save.setToolTip(f"Save current layout into Slot #{slot_num}")
            btn_save.setProperty("class", "slot_save_btn")
            btn_save.clicked.connect(lambda _, s=slot_num: self.save_slot_preset(s))

            btn_del = QPushButton("✕")
            btn_del.setToolTip(f"Clear Slot #{slot_num}")
            btn_del.setProperty("class", "item_del_btn")
            btn_del.clicked.connect(lambda _, s=slot_num: self.clear_slot_preset(s))

            row_l.addWidget(btn_slot, 1)
            row_l.addWidget(btn_save, 0)
            row_l.addWidget(btn_del, 0)
            row_w.setLayout(row_l)

            preset_sidebar_layout.addWidget(row_w)
            self.slot_buttons[slot_num] = btn_slot

        self.preset_sidebar.setLayout(preset_sidebar_layout)
        self.preset_sidebar.setVisible(False)

        # 1. Main Controls
        main_controls = QWidget()
        main_controls.setObjectName("main_controls_container")
        v_layout = QVBoxLayout()
        v_layout.setContentsMargins(0, 0, 0, 0)
        v_layout.setSpacing(8)

        btn_add_img = QPushButton("Add PNG / GIF / Image")
        btn_add_img.clicked.connect(self.open_image_dialog)

        self.btn_select_shape = QPushButton("Select Shape to Add >")
        self.btn_select_shape.setCheckable(True)
        self.btn_select_shape.clicked.connect(self.toggle_shape_sidebar)

        self.btn_lock = QPushButton("Edit Mode (Draggable)")
        self.btn_lock.setObjectName("lock_btn")
        self.btn_lock.setCheckable(True)
        self.btn_lock.setToolTip("Click to lock overlay and allow clicks to pass through to desktop")
        self.btn_lock.clicked.connect(self.toggle_click_through)

        btn_quick_save = QPushButton("Save Preset")
        btn_quick_save.setObjectName("save_btn")
        btn_quick_save.setToolTip("Quick save current layout to Slot #1")
        btn_quick_save.clicked.connect(lambda: self.save_slot_preset(1))

        self.btn_toggle_presets = QPushButton("< Load Preset")
        self.btn_toggle_presets.setObjectName("load_btn")
        self.btn_toggle_presets.setCheckable(True)
        self.btn_toggle_presets.setToolTip("Toggle preset slots sidebar")
        self.btn_toggle_presets.clicked.connect(self.toggle_preset_sidebar)

        btn_clear = QPushButton("Clear All")
        btn_clear.setObjectName("clear_btn")
        btn_clear.clicked.connect(self.clear_all)

        v_layout.addWidget(btn_add_img)
        v_layout.addWidget(self.btn_select_shape)
        v_layout.addWidget(self.btn_lock)
        v_layout.addWidget(btn_quick_save)
        v_layout.addWidget(self.btn_toggle_presets)
        v_layout.addWidget(btn_clear)
        main_controls.setLayout(v_layout)

        # 2. Shape Selection Sidebar
        self.shape_sidebar = QWidget()
        self.shape_sidebar.setObjectName("shape_sidebar_container")
        shape_sidebar_layout = QVBoxLayout()
        shape_sidebar_layout.setContentsMargins(0, 0, 0, 0)
        shape_sidebar_layout.setSpacing(8)

        for st in ["square", "circle", "triangle", "star"]:
            btn = QPushButton(st.capitalize())
            btn.clicked.connect(lambda _, s=st: self.add_shape(s))
            shape_sidebar_layout.addWidget(btn)

        self.shape_sidebar.setLayout(shape_sidebar_layout)
        self.shape_sidebar.setVisible(False)

        # 3. Individual Animation Controls Container (Enlarged)
        items_panel = QWidget()
        items_panel.setObjectName("items_panel_container")
        items_v_layout = QVBoxLayout()
        items_v_layout.setContentsMargins(0, 0, 0, 0)
        items_v_layout.setSpacing(6)

        title = QLabel("Float Toggles")
        items_v_layout.addWidget(title)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFixedHeight(210)  # Height expanded to fill panel
        scroll.setMinimumWidth(240)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        scroll.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)

        self.scroll_content = QWidget()
        self.scroll_layout = QVBoxLayout()
        self.scroll_layout.setContentsMargins(8, 8, 8, 8)
        self.scroll_layout.setSpacing(6)
        self.scroll_content.setLayout(self.scroll_layout)
        scroll.setWidget(self.scroll_content)

        items_v_layout.addWidget(scroll)
        items_panel.setLayout(items_v_layout)

        main_layout.addWidget(self.preset_sidebar)
        main_layout.addWidget(main_controls)
        main_layout.addWidget(self.shape_sidebar)
        main_layout.addWidget(items_panel, 1)

        self.setLayout(main_layout)
        self.setMinimumSize(440, 260)

        # Load preset database on startup
        self.read_presets_from_file()
        if 1 in self.presets_data:
            self.load_slot_preset(1)

    def paintEvent(self, event):
        opt = QStyleOption()
        opt.initFrom(self)
        p = QPainter(self)
        self.style().drawPrimitive(self.style().PrimitiveElement.PE_Widget, opt, p, self)

    def toggle_click_through(self):
        is_locked = self.btn_lock.isChecked()
        self.overlay.set_click_through(is_locked)
        if is_locked:
            self.btn_lock.setText("Locked (Click-Through)")
        else:
            self.btn_lock.setText("Edit Mode (Draggable)")

    def add_shape(self, shape_type: str):
        item = self.overlay.add_shape_sticker(shape_type)
        if item:
            self.add_toggle_row(item)

    def open_image_dialog(self):
        file_path, _ = QFileDialog.getOpenFileName(
            self,
            "Select Image or GIF",
            "",
            "Image Files (*.gif *.png *.jpg *.jpeg *.webp)",
        )
        if file_path:
            item = self.overlay.add_image_sticker(file_path)
            if item:
                self.add_toggle_row(item)
            else:
                QMessageBox.warning(
                    self,
                    "Invalid Media",
                    f"Could not load the selected image or GIF:\n{os.path.basename(file_path)}",
                )

    def add_toggle_row(self, item):
        row_widget = QWidget()
        row_layout = QHBoxLayout()
        row_layout.setContentsMargins(0, 0, 0, 0)
        row_layout.setSpacing(4)

        chk = QCheckBox(f"Float {item.item_name}")
        chk.setChecked(item.is_animated)

        def on_toggle(state):
            is_active = state == Qt.CheckState.Checked.value or state is True
            item.set_animation_active(is_active)
            self.overlay.update_timer_state()

        chk.stateChanged.connect(on_toggle)

        btn_del = QPushButton("✕")
        btn_del.setToolTip(f"Delete {item.item_name}")
        btn_del.setProperty("class", "item_del_btn")
        btn_del.setCursor(Qt.CursorShape.PointingHandCursor)

        def on_delete():
            self.overlay.remove_item(item)
            row_widget.deleteLater()

        btn_del.clicked.connect(on_delete)

        row_layout.addWidget(chk, 1)
        row_layout.addWidget(btn_del, 0)
        row_widget.setLayout(row_layout)
        self.scroll_layout.addWidget(row_widget)

    def toggle_shape_sidebar(self):
        is_expanded = self.btn_select_shape.isChecked()
        if is_expanded and self.btn_toggle_presets.isChecked():
            self.btn_toggle_presets.setChecked(False)
            self.preset_sidebar.setVisible(False)
            self.btn_toggle_presets.setText("< Load Preset")

        self.shape_sidebar.setVisible(is_expanded)
        self.btn_select_shape.setText(
            "Select Shape to Add <" if is_expanded else "Select Shape to Add >"
        )
        self.adjustSize()

    def toggle_preset_sidebar(self):
        is_expanded = self.btn_toggle_presets.isChecked()
        if is_expanded and self.btn_select_shape.isChecked():
            self.btn_select_shape.setChecked(False)
            self.shape_sidebar.setVisible(False)
            self.btn_select_shape.setText("Select Shape to Add >")

        self.preset_sidebar.setVisible(is_expanded)
        self.btn_toggle_presets.setText(
            "Load Preset >" if is_expanded else "< Load Preset"
        )
        self.adjustSize()

    def read_presets_from_file(self):
        """Reads layout_presets.json and updates UI slot button text."""
        config_path = get_config_path()
        if not os.path.exists(config_path):
            self.presets_data = {}
        else:
            try:
                with open(config_path, "r", encoding="utf-8") as f:
                    raw = json.load(f)
                    self.presets_data = {int(k): v for k, v in raw.items()}
            except Exception as e:
                print(f"Failed to read presets: {e}")
                self.presets_data = {}

        self.update_preset_ui_slots()

    def update_preset_ui_slots(self):
        """Updates slot buttons to display [Saved (N items)] or [Empty]."""
        for slot_num in range(1, 6):
            btn = self.slot_buttons.get(slot_num)
            if not btn:
                continue
            if slot_num in self.presets_data and self.presets_data[slot_num]:
                count = len(self.presets_data[slot_num])
                btn.setText(f"Slot #{slot_num} ({count} items)")
            else:
                btn.setText(f"Slot #{slot_num} [Empty]")

    def write_presets_to_file(self):
        """Writes preset dictionary to layout_presets.json."""
        config_path = get_config_path()
        try:
            with open(config_path, "w", encoding="utf-8") as f:
                json.dump(self.presets_data, f, indent=4)
        except Exception as e:
            QMessageBox.warning(self, "Save Error", f"Failed to save preset: {e}")

    def save_slot_preset(self, slot_num: int):
        """Saves active screen stickers into a specific preset slot (1-5)."""
        data = [
            item.to_dict()
            for item in self.overlay.items_list
            if hasattr(item, "to_dict")
        ]
        self.presets_data[slot_num] = data
        self.write_presets_to_file()
        self.update_preset_ui_slots()

    def load_slot_preset(self, slot_num: int):
        """Loads stickers from a specific preset slot (1-5)."""
        if slot_num not in self.presets_data or not self.presets_data[slot_num]:
            return

        self.clear_all_ui_only()

        data = self.presets_data[slot_num]
        for entry in data:
            if entry.get("type") == "image":
                item = self.overlay.add_image_sticker(
                    file_path=entry["file_path"],
                    x=entry["x"],
                    y=entry["y"],
                    is_animated=entry.get("is_animated", True),
                    custom_name=entry.get("name"),
                )
                if item:
                    self.add_toggle_row(item)

            elif entry.get("type") == "shape":
                item = self.overlay.add_shape_sticker(
                    shape_type=entry["shape_type"],
                    x=entry["x"],
                    y=entry["y"],
                    is_animated=entry.get("is_animated", True),
                    custom_name=entry.get("name"),
                )
                if item:
                    self.add_toggle_row(item)

    def clear_slot_preset(self, slot_num: int):
        """Clears a specific slot from saved presets file without affecting current screen items."""
        if slot_num in self.presets_data:
            del self.presets_data[slot_num]
            self.write_presets_to_file()
            self.update_preset_ui_slots()

    def clear_all_ui_only(self):
        """Clears screen items and UI toggles without modifying layout_presets.json."""
        self.overlay.clear_all_stickers()
        while self.scroll_layout.count():
            child = self.scroll_layout.takeAt(0)
            if child.widget():
                child.widget().deleteLater()

    def clear_all(self):
        """Clears active screen stickers and UI toggles without touching saved preset file slots."""
        self.clear_all_ui_only()

    def closeEvent(self, event):
        self.save_slot_preset(1)  # Auto-saves active layout to Slot #1 on exit
        self.overlay.close()
        QApplication.quit()
        event.accept()

    def keyPressEvent(self, event):
        if event.key() == Qt.Key.Key_Escape:
            if self.overlay.isVisible():
                self.overlay.hide()
            else:
                self.overlay.show()
            event.accept()
        else:
            super().keyPressEvent(event)


if __name__ == "__main__":
    try:
        ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(
            "overlay.customization.controller"
        )
    except Exception:
        pass

    app = QApplication(sys.argv)

    icon_path = resource_path("icon.png")
    if os.path.exists(icon_path):
        app.setWindowIcon(QIcon(icon_path))

    overlay = DesktopOverlay()
    overlay.show()

    panel = ControlPanel(overlay)
    panel.show()

    sys.exit(app.exec())