# Správce souborů

Vlastní správce souborů pro Android (Poco M6 Pro).

- Hlavní úložiště, SD karta, Stažené, kategorie (obrázky, videa, audio, dokumenty, aplikace)
- Poslední fotky a poslední soubory (30 dní), seskupené podle dnů
- Kopírování, přesun, přejmenování, sdílení, koš s obnovením
- **Trezor** – soubory zašifrované AES-256-GCM, odemykání PINem nebo otiskem prstu.
  Trezor je ve skryté složce `.TrezorSpravce` na úložišti, přežije přeinstalaci aplikace
  a dá se (zašifrovaný) zálohovat FolderSyncem na NAS.
- **NAS přes SFTP** (Raspberry Pi + OpenMediaVault) – procházení, stahování, nahrávání, mazání

APK se sestaví automaticky po každé změně: záložka **Releases** → `SpravceSouboru.apk`.
