# Groš

Offline zápisník výdavkov, príjmov a prevodov pre Android. Transakcia sa zapíše za pár sekúnd: **+ → suma → kategória → Uložiť**. Účet a dátum sú predvyplnené. Každý záznam sa dá neskôr upraviť.

Appka nemá povolenie na internet, nemá konto, analytiku, reklamy ani hlásenie pádov. Dáta neopustia telefón, kým ich sám neexportuješ.

## Čo vie

- **Účty**: bežný, hotovosť, sporenie, investície a iný. Majú počiatočný stav, prepínač „V súčte“, opravu zostatku a archiváciu.
- **Transakcie**: výdavok, príjem a prevod medzi účtami.
  - Úprava všetkých polí.
  - Zmazanie s potvrdením a možnosťou Späť.
  - Poznámka s našepkávaním.
- **Zoznam transakcií**:
  - zoskupený po dňoch,
  - filter podľa účtu, kategórie a obdobia,
  - hľadanie v poznámkach,
  - súčet nad výsledkom.
- **Kategórie**: dve úrovne (napr. Auto → Tankovanie), osobitne výdavkové a príjmové.
  - Farba, poradie, presun, zlúčenie, archivácia.
- **Opakované platby**: každých N týždňov, mesiacov alebo rokov.
  - Zmeškané výskyty sa dobehnú pri ďalšom spustení.
- **Rozpočty**: mesačný limit na hlavnú výdavkovú kategóriu vrátane jej podkategórií.
- **Reporty**: za mesiac alebo rok.
  - Príjmy, výdavky, bilancia a rozpis podľa kategórií.
  - Za rok aj graf po mesiacoch.
- **Widget** na plochu: tlačidlo, ktoré otvorí Novú transakciu.
- **Zámok appky**: odtlačok, tvár alebo PIN telefónu.
- **Šifrovaná záloha**: export do jedného súboru a import, aj priamo v úvodnom sprievodcovi.
- **Vzhľad**: tmavá, svetlá alebo systémová téma a tri veľkosti písma.

Vyžaduje Android 14 alebo novší. Funguje aj bez Google Play Services, napríklad na GrapheneOS.

## Inštalácia cez Obtainium

1. Nainštaluj si [Obtainium](https://github.com/ImranR98/Obtainium).
2. Pridaj appku s adresou `https://github.com/mstajanc/gros`.
3. Obtainium stiahne APK z najnovšieho vydania v [Releases](https://github.com/mstajanc/gros/releases) a bude ponúkať aktualizácie.

Každé vydanie je podpísané tým istým kľúčom. Odtlačok certifikátu (SHA-256):

```
5f86986c74de583c8484ce07eee88b319d3e5f0f739db782dc9c4ad96bd4ddd5
```

Ak chceš stiahnuté APK overiť ručne, odtlačok zistíš príkazom `apksigner verify --print-certs gros-X.Y.Z.apk`. Poznámky k vydaniu uvádzajú aj SHA-256 samotného APK.

## Súkromie a bezpečnosť

- **Žiadna sieť.** Manifest neobsahuje povolenie `INTERNET` ani `ACCESS_NETWORK_STATE`. Build to overuje automaticky.
- **Žiadne systémové zálohy.** Databáza nejde do záloh Androidu (`allowBackup="false"`). Jediná kópia dát mimo telefónu je teda tvoj manuálny export.
- **Zámok je brána v rozhraní, nie šifrovanie databázy.** So zapnutým zámkom appka:
  - pri spustení a po minúte na pozadí vyžiada odtlačok, tvár alebo PIN telefónu;
  - nič nevykreslí pred overením;
  - zakáže snímky obrazovky a náhľad v prehľade úloh.

  Samotná databáza v telefóne však šifrovaná nie je. Pred niekým, kto má prístup k súborom zariadenia, ju chráni až šifrovanie telefónu.

## Záloha

Záloha je jeden súbor chránený heslom, ktoré si zvolíš pri exporte (aspoň 10 znakov).

- **Šifrovanie**: AES-256-GCM.
- **Kľúč**: odvodený z hesla cez PBKDF2-HMAC-SHA256 so 600 000 iteráciami a náhodnou soľou.
- **Hlavička súboru**: `GROS`, verzia formátu, počet iterácií, soľ a nonce. Je chránená ako súčasť overenia (AAD).
- **Obsah**: komprimovaný JSON so všetkými účtami, kategóriami, transakciami, pravidlami a rozpočtami.
- **Import**:
  - obsah najprv celý overí;
  - potom nahradí všetky súčasné dáta v jednej transakcii, takže sa nič nezapíše napoly;
  - nesprávne heslo alebo poškodený súbor ohlási bez zmeny dát.

Bez hesla sa zo zálohy nedá nič obnoviť. Heslo sa nikam neukladá.

## Zostavenie zo zdrojákov

Potrebuješ JDK 21 alebo novší a Android SDK s platformou 37.2.

```
./gradlew assembleDebug          # ladiace APK v app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit testy (Robolectric)
./gradlew lintRelease            # lint
```

Release build sa podpisuje kľúčom mimo repozitára. Cestu k súboru `keystore.properties` určuje premenná `GROS_KEYSTORE_PROPERTIES`; predvolene je to `~/.gros/keystore.properties`. Súbor obsahuje `storeFile`, `storePassword`, `keyAlias` a `keyPassword`. Bez neho sa release build nepodpíše.

Technológie: Kotlin, Jetpack Compose (Material 3), Room, DataStore. Nepoužíva Google Play Services ani Firebase.

Podrobná špecifikácia je v [docs/SPEC.md](docs/SPEC.md).

## Licencia

Groš je slobodný softvér pod licenciou [GPL-3.0](LICENSE).

Použité diela tretích strán:

- písmo [Inter](https://rsms.me/inter/): SIL Open Font License, [licenses/Inter-OFL.txt](licenses/Inter-OFL.txt);
- písmo [Space Grotesk](https://github.com/floriankarsten/space-grotesk): SIL Open Font License, [licenses/SpaceGrotesk-OFL.txt](licenses/SpaceGrotesk-OFL.txt);
- ikony [Lucide](https://lucide.dev): licencia ISC, [licenses/Lucide-ISC.txt](licenses/Lucide-ISC.txt).
