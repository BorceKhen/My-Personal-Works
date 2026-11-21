"""mDNS / DNS-SD broadcaster and browser for `_remotedisplay._tcp.` via zeroconf."""

from __future__ import annotations

import socket
from typing import Callable, Dict, List, Optional

from zeroconf import IPVersion, ServiceBrowser, ServiceInfo, ServiceListener, Zeroconf

from .protocol import DEFAULT_WS_PORT, SERVICE_TYPE, DiscoveredHost, get_primary_lan_ip


def build_service_info(
    device_name: str,
    ws_port: int = DEFAULT_WS_PORT,
    ip_address: Optional[str] = None,
    os_type: str = "windows",
) -> ServiceInfo:
    """Construct a zeroconf ServiceInfo record matching Android NsdDiscoveryManager."""
    ip = ip_address or get_primary_lan_ip()
    safe_name = device_name.replace(".", "-")[:32] or "WindowsHost"
    return ServiceInfo(
        type_=SERVICE_TYPE,
        name=f"DeckCast-{safe_name}.{SERVICE_TYPE}",
        addresses=[socket.inet_aton(ip)],
        port=ws_port,
        properties={
            "deviceName": device_name.encode("utf-8"),
            "osType": os_type.encode("utf-8"),
            "wsPort": str(ws_port).encode("utf-8"),
        },
        server=f"{safe_name}.local.",
    )


class MdnsAdvertiser:
    """Advertises this Windows machine as a DeckCast Host on the local LAN."""

    def __init__(self, device_name: Optional[str] = None, ws_port: int = DEFAULT_WS_PORT) -> None:
        self.device_name = device_name or socket.gethostname()
        self.ws_port = ws_port
        self._zc: Optional[Zeroconf] = None
        self._info: Optional[ServiceInfo] = None

    def start(self) -> None:
        if self._zc is not None:
            return
        self._zc = Zeroconf(ip_version=IPVersion.V4Only)
        self._info = build_service_info(self.device_name, self.ws_port)
        self._zc.register_service(self._info)

    def stop(self) -> None:
        if self._zc is not None:
            if self._info is not None:
                try:
                    self._zc.unregister_service(self._info)
                except Exception:
                    pass
            try:
                self._zc.close()
            except Exception:
                pass
        self._zc = None
        self._info = None


class MdnsBrowser:
    """Discovers Android & Windows DeckCast Hosts on the local LAN."""

    def __init__(self, on_hosts_updated: Callable[[List[DiscoveredHost]], None]) -> None:
        self._on_hosts_updated = on_hosts_updated
        self._zc: Optional[Zeroconf] = None
        self._browser: Optional[ServiceBrowser] = None
        self._hosts: Dict[str, DiscoveredHost] = {}

    def start(self) -> None:
        if self._zc is not None:
            return
        self._zc = Zeroconf(ip_version=IPVersion.V4Only)
        zc_ref = self._zc
        outer = self

        class _Listener(ServiceListener):
            def add_service(self, zc: Zeroconf, type_: str, name: str) -> None:
                info = zc.get_service_info(type_, name, timeout=2000)
                if info is not None:
                    outer._handle_info(name, info)

            def update_service(self, zc: Zeroconf, type_: str, name: str) -> None:
                info = zc.get_service_info(type_, name, timeout=2000)
                if info is not None:
                    outer._handle_info(name, info)

            def remove_service(self, zc: Zeroconf, type_: str, name: str) -> None:
                if outer._hosts.pop(name, None) is not None:
                    outer._emit()

        self._browser = ServiceBrowser(zc_ref, SERVICE_TYPE, _Listener())

    def stop(self) -> None:
        if self._browser is not None:
            try:
                self._browser.cancel()
            except Exception:
                pass
        if self._zc is not None:
            try:
                self._zc.close()
            except Exception:
                pass
        self._browser = None
        self._zc = None
        self._hosts.clear()

    def _handle_info(self, name: str, info: ServiceInfo) -> None:
        addrs = info.parsed_addresses(IPVersion.V4Only)
        if not addrs:
            return
        props = {
            (k.decode("utf-8", errors="ignore") if isinstance(k, bytes) else str(k)): (
                v.decode("utf-8", errors="ignore") if isinstance(v, bytes) else str(v)
            )
            for k, v in (info.properties or {}).items()
            if v is not None
        }
        device_name = props.get("deviceName") or name.split(".")[0]
        os_type = props.get("osType", "unknown")
        try:
            ws_port = int(props.get("wsPort") or info.port or DEFAULT_WS_PORT)
        except ValueError:
            ws_port = info.port or DEFAULT_WS_PORT

        self._hosts[name] = DiscoveredHost(
            service_name=name,
            device_name=device_name,
            os_type=os_type,
            host_address=addrs[0],
            ws_port=ws_port,
        )
        self._emit()

    def _emit(self) -> None:
        hosts = sorted(self._hosts.values(), key=lambda h: h.device_name.lower())
        self._on_hosts_updated(hosts)
