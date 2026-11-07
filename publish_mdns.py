import socket, dbus
from gi.repository import GLib
bus=dbus.SystemBus()
server=dbus.Interface(bus.get_object('org.freedesktop.Avahi','/'),'org.freedesktop.Avahi.Server')
group_path=server.EntryGroupNew()
group=dbus.Interface(bus.get_object('org.freedesktop.Avahi',group_path),'org.freedesktop.Avahi.EntryGroup')
with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as sock:
    sock.connect(('192.168.0.34',5555)); address=sock.getsockname()[0]
group.AddAddress(dbus.Int32(-1),dbus.Int32(0),dbus.UInt32(16),'remote.local',address)
group.AddService(dbus.Int32(-1),dbus.Int32(0),dbus.UInt32(0),'Living Room TV Remote','_http._tcp','local','remote.local',dbus.UInt16(8765),[dbus.ByteArray(b'path=/')])
group.Commit()
print('Publishing remote.local at '+address,flush=True)
GLib.MainLoop().run()
