# Root Certificate Manager KernelSU module

This module is installed automatically by the companion Android app.

Managed certificates live in:

```
/data/adb/modules/root_cert_manager/system/etc/security/cacerts/
```

Android 13 and older use KernelSU's system overlay. Android 14 and newer build
an in-memory copy of the Conscrypt APEX trust store, add the managed
certificates, and bind that copy into the init and Zygote mount namespaces.

The injection log is written to `/data/local/tmp/root_cert_manager.log`.
