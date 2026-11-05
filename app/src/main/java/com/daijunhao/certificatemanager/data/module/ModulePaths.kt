package com.daijunhao.certificatemanager.data.module

object ModulePaths {
    const val MODULE_ID = "root_cert_manager"
    const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
    const val CERT_DIR = "$MODULE_DIR/system/etc/security/cacerts"
    const val LOG_FILE = "/data/local/tmp/root_cert_manager.log"

    const val EXPECTED_VERSION_NAME = "1.0.0"
    const val EXPECTED_VERSION_CODE = 1
}
