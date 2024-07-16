# Share To InputStick (Android)

Adds an entry to Android's Share menu to directly share text to an [InputStick](http://inputstick.com/).

# Building

Building can either be done through Android Studio (not reproducible!) or the build.sh script in this repository (reproducibly with OpenJDK 17). This script can also sign the build.

First, ensure your git submodule is up-to-date:
```
git submodule --init
```

Build without signing:
```
./build.sh
```

Build with signing:
```
KEYSTORE=/path/to/keystore KEYSTORE_ALIAS=key0 ./build.sh
```

## License

MIT
