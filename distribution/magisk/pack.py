import pathlib,re,subprocess,sys,tempfile,zipfile
here=pathlib.Path(__file__).resolve().parent
apk=pathlib.Path(sys.argv[1]).resolve()
prop=(here/'module.prop').read_text()
version=re.search(r'^version=(.+)$',prop,re.M)[1]
badging=subprocess.check_output(['aapt','dump','badging',str(apk)],text=True)
pkg=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
if not pkg or pkg[1]!='com.noteVE' or pkg[3]!=version:raise SystemExit('APK identity/version mismatch')
subprocess.run(['apksigner','verify',str(apk)],check=True)
out=here.parent/f'NoteVE-PrivApp-Magisk-{version}.zip'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
 for name in ['module.prop','customize.sh','service.sh']:z.write(here/name,name)
 z.write(apk,'system/priv-app/NoteVE/NoteVE.apk')
 z.write(here.parent.parent/'com.noteVE.xml','system/etc/permissions/com.noteVE.xml')
print(out)
