#!/system/bin/sh

ui_print "- Root Certificate Manager module"

mkdir -p "$MODPATH/system/etc/security/cacerts"
rm -f "$MODPATH/system/etc/security/cacerts/.keep"

set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/apex-inject.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm_recursive "$MODPATH/system/etc/security/cacerts" 0 0 0755 0644

ui_print "- Open the Root Certificate Manager app to import certificates"
ui_print "- Reboot after importing or updating certificates"
