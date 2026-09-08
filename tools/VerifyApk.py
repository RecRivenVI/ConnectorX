"""Check definitions in the actual APK, not string references or separate compilation outputs."""
import re
import struct
import sys
import zipfile


def definitions(data):
    if not data.startswith(b"dex\n"):
        raise ValueError("Not a DEX file")

    def u32(offset):
        return struct.unpack_from("<I", data, offset)[0]

    def uleb(offset):
        value = 0
        for shift in range(0, 35, 7):
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value, offset
        raise ValueError("Invalid ULEB128")

    strings = []
    for index in range(u32(56)):
        _, offset = uleb(u32(u32(60) + index * 4))
        strings.append(data[offset:data.index(b"\0", offset)].decode("utf-8", errors="replace"))
    types = [strings[u32(u32(68) + index * 4)] for index in range(u32(64))]
    method_offset = u32(92)
    for index in range(u32(96)):
        offset = u32(100) + index * 32
        name = types[u32(offset)]
        cursor = u32(offset + 24)
        methods = set()
        if cursor:
            counts = []
            for _ in range(4):
                count, cursor = uleb(cursor)
                counts.append(count)
            for _ in range(counts[0] + counts[1]):
                _, cursor = uleb(cursor)
                _, cursor = uleb(cursor)
            for count in counts[2:]:
                method_index = 0
                for _ in range(count):
                    delta, cursor = uleb(cursor)
                    method_index += delta
                    _, cursor = uleb(cursor)
                    _, cursor = uleb(cursor)
                    methods.add(strings[u32(method_offset + method_index * 8 + 4)])
        yield name, methods


def verify(path):
    classes = {}
    duplicates = []
    with zipfile.ZipFile(path) as apk:
        for name in apk.namelist():
            if re.fullmatch(r"classes\d*\.dex", name):
                for descriptor, methods in definitions(apk.read(name)):
                    if descriptor in classes:
                        duplicates.append(descriptor)
                    classes[descriptor] = methods
    if duplicates:
        raise ValueError("Duplicate DEX class definitions: " + ", ".join(duplicates[:20]))
    required = {
        "ui/devices/AndroidDevicesAdapterKt": "androidDevices",
        "ui/devices/DevicesPageKt": "DevicesPage",
        "platform/PlatformStorageKt": "PlatformStorage",
        "ui/navigation/AppShellKt": "AppShell",
        "ui/theme/ConnectorThemeKt": "ConnectorTheme",
    }
    for owner, method in required.items():
        descriptor = "Lio/github/recrivenvi/connectorx/" + owner + ";"
        # Kotlin value-class parameters (for example Dp) add a JVM name suffix.
        if not any(name == method or re.fullmatch(re.escape(method) + r"-[A-Za-z0-9_-]+", name)
                   for name in classes.get(descriptor, set())):
            raise ValueError(f"Missing executable definition: {owner}.{method}")
    print(f"PASS: {len(classes)} unique DEX classes; Android/shared UI entry points are present")


if __name__ == "__main__":
    verify(sys.argv[1])
