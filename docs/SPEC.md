# Groš – špecifikácia pre Claude Code

15. 11. 2026 · Mirec · aktualizované 16. 11. 2026 (podkategórie, rozhodnutia z kroku 1 – pozri koniec dokumentu)

## Cieľ a princípy

Groš je offline Android appka na zápis výdavkov, príjmov a prevodov, kde zápis transakcie trvá do 5 sekúnd a každý záznam sa dá dodatočne upraviť. Vizuálna predloha: [UI návrh – 9 obrazoviek](https://claude.ai/artifact/WHcTnvQbjcVjW4hm4F3dtT). Pri konflikte medzi mockupom a týmto dokumentom platí tento dokument.

- **Rýchlosť zápisu je metrika č. 1.** Tok: tlačidlo + → suma → kategória → Uložiť. Účet a dátum sú predvyplnené (posledný použitý účet, dnešok).
- **Všetko je editovateľné.** Transakcie, prevody, opakované platby, účty, kategórie aj rozpočty majú úpravu a zmazanie alebo archiváciu. Žiadny záznam nie je „zamknutý“.
- **Transakcia má 4 povinné polia:** suma, kategória, účet, dátum. Nepovinné sú len podkategória a poznámka (predvolene skrytá za „+ Poznámka“).
- **Žiadna sieť.** Manifest neobsahuje povolenie INTERNET. Žiadne konto, analytika, reklamy, crash reporting.
- **Jedna mena (EUR).** Sumy sa ukladajú ako celé centy (Long), nikdy ako float.
- **Platforma:** Android 14+ (minSdk 34), cieľové zariadenie GrapheneOS bez Google Play Services. Licencia GPL-3.0. Jazyk UI: slovenčina (texty v strings.xml, pripravené na preklad).

## Rozsah

MVP obsahuje 10 funkcií; všetko ostatné je zámerne vynechané a Claude Code to nemá pridávať.

**V MVP**

1. **Účty** – názov, typ (bežný, hotovosť, sporenie, investície, iný), počiatočný stav, prepínač „V súčte“, oprava zostatku, archivácia.
2. **Transakcie** – výdavok, príjem, prevod medzi účtami. Úprava všetkých polí vrátane typu, zmazanie s možnosťou Späť (snackbar 5 s). Poznámka našepkáva z predošlých zápisov.
3. **Zoznam transakcií** – zoskupený po dňoch, filter podľa účtu, kategórie a obdobia, hľadanie v poznámke, súčet nad výsledkom.
4. **Kategórie** – dve úrovne (hlavná kategória a jej podkategórie, napr. Auto → Tankovanie, Servis), osobitne výdavkové a príjmové, názov + farba. Vytvorenie, premenovanie, presun, zlúčenie dvoch kategórií, archivácia. Podrobnosti v sekcii Biznis pravidlá – Podkategórie.
5. **Opakované platby** – výdavok, príjem aj prevod; každých N týždňov, mesiacov alebo rokov. Plná úprava, pozastavenie, zmazanie.
6. **Rozpočty** – mesačný limit na hlavnú výdavkovú kategóriu (počíta aj jej podkategórie).
7. **Reporty** – mesiac alebo rok: príjmy, výdavky, bilancia, výdavky podľa kategórií; v ročnom pohľade aj 12 mesačných stĺpcov príjmov a výdavkov.
8. **Widget na plochu** – jedno tlačidlo, otvorí priamo obrazovku Nová transakcia.
9. **Zámok appky** – biometria alebo PIN/heslo zariadenia.
10. **Záloha** – manuálny šifrovaný export a import jedného súboru; pripomienka v appke po 30 dňoch bez zálohy.

Prvé spustenie: sprievodca vytvorí účty s počiatočnými stavmi a ponúkne predvolené kategórie. Tým sa rieši prechod z MoneyWallet; história sa neimportuje.

**Zámerne mimo rozsahu**

- Viac mien a kurzy
- Dlhy, pôžičky, sporiace ciele
- Udalosti, miesta, osoby, štítky, tretia a ďalšia úroveň kategórií
- Prílohy a fotky bločkov
- Synchronizácia, cloud, import z banky, import z MoneyWallet
- Automatická záloha a CSV export
- Systémové notifikácie

## Dátový model

Päť tabuliek v Room; zostatky sa nikdy neukladajú, vždy sa počítajú z transakcií.

| Entita | Polia | Poznámka |
| --- | --- | --- |
| `Account` | id, name, type, openingBalanceCents, includeInTotal, archived, sortOrder | type: CHECKING, CASH, SAVINGS, INVESTMENT, OTHER |
| `Category` | id, parentId?, name, kind, color, archived, sortOrder | kind: EXPENSE alebo INCOME; parentId = null je hlavná kategória, inak podkategória; sortOrder platí v rámci rodiča |
| `Transaction` | id, type, amountCents, date, accountId, toAccountId?, categoryId?, note?, noteSearch?, recurringRuleId?, createdAt, updatedAt | type: EXPENSE, INCOME, TRANSFER; amountCents > 0 |
| `RecurringRule` | id, name, type, amountCents, accountId, toAccountId?, categoryId?, frequency, interval, anchorDate, nextDate, endDate?, paused | frequency: WEEKLY, MONTHLY, YEARLY; interval ≥ 1 (každých N periód); name sa zapíše ako poznámka vygenerovanej transakcie |
| `Budget` | id, categoryId (unique), monthlyLimitCents | len hlavné výdavkové kategórie |

Nastavenia (zámok, téma, posledný použitý účet) sú v Preferences DataStore, nie v databáze.

**Invarianty**

- Prevod je jeden riadok `Transaction` s `accountId` (z) a `toAccountId` (na), bez kategórie. `accountId ≠ toAccountId`.
- Výdavok a príjem majú povinnú `categoryId` správneho `kind` a prázdne `toAccountId`. `categoryId` smie ukazovať na hlavnú kategóriu aj na podkategóriu; to isté platí pre `RecurringRule`.
- Kategórie majú najviac dve úrovne: rodič podkategórie má `parentId = null`. Podkategória má rovnaký `kind` ako rodič a preberá jeho farbu.
- `date` je LocalDate bez času; poradie v rámci dňa určuje `createdAt`.
- Účet ani kategória s transakciami sa nemažú natvrdo: účet sa archivuje, kategória sa archivuje alebo zlúči do inej.
- Zmazanie `RecurringRule` nemaže už zapísané transakcie; ich `recurringRuleId` sa nastaví na null.
- Schéma má verziu a Room migrácie od prvého vydania (exportSchema = true).

**Technické poznámky**

- Entita transakcie sa v kóde volá `TransactionEntity`, aby nekolidovala s Room anotáciou `@Transaction`.
- `date` sa ukladá cez TypeConverter ako ISO text (`RRRR-MM-DD`), `createdAt` a `updatedAt` ako epoch milisekundy v UTC; indexy na `date`, `accountId` a `categoryId`.
- Cudzie kľúče: v `TransactionEntity` aj `RecurringRule` sú `accountId`, `toAccountId` a `categoryId` s RESTRICT; `recurringRuleId` so SET NULL; `Budget.categoryId` s CASCADE; `Category.parentId` s RESTRICT.
- Natvrdo sa dá zmazať len účet alebo kategória, na ktoré neodkazuje žiadna transakcia ani pravidlo; hlavná kategória navyše nesmie mať podkategórie.
- `Category.kind` sa po vytvorení nemení a zlúčiť sa dajú len kategórie rovnakého `kind`. `Category.color` je index do palety kategórií (sekcia Dizajnové tokeny).
- `noteSearch` je odvodený stĺpec (malé písmená, bez diakritiky); pri importe zálohy sa prepočíta. Vstup hľadania sa pred `LIKE` escapuje (`%`, `_`).
- Sumy pravidiel aj limity rozpočtov sú > 0, rovnako ako `amountCents`.
- Invarianty stráži repozitár, nie UI, a pokrývajú ich unit testy.
- Aktuálny dátum sa do `domain` dodáva cez injektovaný `Clock`, aby boli testy deterministické.
- Záporný zostatok účtu je povolený.

## Obrazovky a toky

Spodná lišta má 4 záložky a stredové tlačidlo +: Prehľad · Transakcie · + · Plán · Reporty. Účty sa spravujú z Prehľadu (odkaz Spravovať), Nastavenia sú pod ikonou na Prehľade.

| Obrazovka | Obsah | Akcie |
| --- | --- | --- |
| Prehľad | Súčet účtov „V súčte“, príjmy a výdavky aktuálneho mesiaca, účty, posledných 5 transakcií | Ťuknutie na transakciu = úprava; „Všetky“ = zoznam transakcií; ťuknutie na účet = jeho pohyby |
| Zoznam transakcií | Transakcie po dňoch, filter účet / kategória / obdobie, hľadanie, súčet nad výsledkom | Úprava, potiahnutie = zmazanie so Späť |
| Nová / úprava transakcie | Typ, suma, kategória, účet, dátum, poznámka | Uložiť; pri úprave aj Zmazať a Duplikovať |
| Účty | Zoznam účtov so zostatkom a prepínačom „V súčte“ | Ťuknutie = pohyby účtu; ceruzka = úprava, oprava zostatku, archivácia; pridať účet; prevod |
| Plán – Opakované | Pravidlá s ďalším dátumom, súhrn prepočítaný na mesiac | Pridať, upraviť, pozastaviť, zmazať |
| Plán – Rozpočty | Limit, čerpanie a zostatok na kategóriu | Pridať, upraviť limit, zmazať |
| Reporty | Mesiac alebo rok: bilancia, príjmy, výdavky, hlavné kategórie zoradené od najväčšej (súčet vrátane podkategórií) | Ťuknutie na kategóriu bez podkategórií = zoznam jej transakcií; s podkategóriami = rozbalenie rozpisu po podkategóriách |
| Nastavenia | Kategórie, zámok, téma, záloha, o aplikácii | Export, import |

**Nová transakcia (hlavný tok)**

1. Tlačidlo + alebo widget otvorí obrazovku s aktívnou číselnou klávesnicou appky; typ je Výdavok.
2. Používateľ zadá sumu a ťukne na kategóriu. Kategórie majú pevné poradie, ktoré si používateľ určí v Nastaveniach; mriežka ukazuje prvých 7 hlavných kategórií a dlaždicu „Viac“ so zoznamom všetkých. Ak má vybraná kategória podkategórie, pod mriežkou sa zobrazí riadok ich čipov; výber podkategórie je nepovinný a Uložiť je aktívne aj bez neho.
3. Účet je posledný použitý, dátum dnešný; oboje sa dá zmeniť jedným ťuknutím.
4. Uložiť zavrie obrazovku a zobrazí snackbar „Uložené“ s akciou Späť; pri otvorení z widgetu sa po uložení ukáže Prehľad.

Pri type Prevod sa mriežka kategórií nahradí poľom Na účet a pole Účet sa premenuje na Z účtu. Tlačidlo Uložiť je neaktívne, kým nie je suma > 0 a vybraná kategória (alebo oba účty).

Zmena typu pri úprave: výdavok ↔ príjem vynuluje kategóriu a vyžiada novú; zmena na prevod kategóriu odstráni a vyžiada cieľový účet.

**Hľadanie, filtre a poznámka**

- Hľadanie prehľadáva poznámku naprieč všetkými účtami bez ohľadu na veľkosť písmen a diakritiku („alza“ nájde „Alza“). Na to sa pri uložení drží normalizovaná kópia poznámky v `noteSearch`.
- Filtre účet, kategória a obdobie sa dajú kombinovať navzájom aj s hľadaním.
- Nad výsledkom je počet zobrazených transakcií a súčet ich príjmov a výdavkov; prevody sa do súčtu nepočítajú. Pri filtri jedného účtu sa navyše ukáže jeho aktuálny zostatok.
- Ťuknutie na účet na Prehľade alebo v zozname Účty otvorí Transakcie s filtrom tohto účtu, vrátane prevodov z neho a naň. Úprava účtu je pod ikonou ceruzky.
- Poznámka našepkáva: od 2 znakov ponúkne najviac 5 predošlých poznámok obsahujúcich zadaný text, zoradených podľa počtu použití; ťuknutie doplní celú poznámku. Poznámka ostáva nepovinný voľný text.

**Okrajové stavy**

- Vždy existuje aspoň jeden aktívny účet: sprievodca ho vyžaduje a posledný aktívny účet sa nedá archivovať. Typ Prevod je dostupný až pri dvoch aktívnych účtoch.
- Ak posledný použitý účet neexistuje alebo je archivovaný, predvyplní sa prvý aktívny účet podľa poradia.
- Archivované účty a kategórie sa pri novom zázname neponúkajú; ostávajú zobrazené na záznamoch, ktoré ich už používajú, a vo filtroch.
- Zmazanie transakcie sa vykoná hneď a Späť ju vloží späť s pôvodným `id`. Zmazanie pravidla, zmazanie rozpočtu a import vyžadujú potvrdzovací dialóg.
- Systémové Späť na rozpracovanom formulári zahodí zmeny bez dialógu. Rozpracovaný formulár prežije otočenie obrazovky aj obnovenie procesu (`SavedStateHandle`).
- Počty sa skloňujú cez `plurals` (1 transakcia, 3 transakcie, 5 transakcií).
- Poradie účtov a kategórií sa mení potiahnutím v ich zoznamoch.
- Widget používa `PendingIntent` s `FLAG_IMMUTABLE`; Activity je `singleTop` a spracuje `onNewIntent`, takže widget otvorí Novú transakciu aj pri už bežiacej appke.

## Responzivita

Mockup je kreslený pre jednu veľkosť (390 × 844 px); appka sa musí prispôsobiť každej veľkosti okna sama.

| Situácia | Požiadavka |
| --- | --- |
| Telefón na výšku, šírka 360–480 dp | Primárny cieľ. Rozmery v dp, šírky cez `weight` a `fillMaxWidth`, žiadne pevné šírky obsahu |
| Veľké písmo (do 200 %) | Texty v sp. Každá obrazovka sa zvislo posúva; názvy sa smú zalomiť, sumy nikdy |
| Nová transakcia | Pri výške okna ≥ 600 dp sú klávesnica a Uložiť prichytené dole a posúva sa len časť nad nimi; pri nižšom okne sa posúva celá obrazovka |
| Na šírku | Podporované bez vlastného rozloženia; orientácia sa nezamyká |
| Tablet a rozložený foldable (šírka ≥ 600 dp) | Obsah vycentrovaný s max. šírkou 600 dp; dvojpanelové rozloženie nie je v MVP |
| Systémové lišty | Edge-to-edge s `WindowInsets`; funguje s gestami aj s 3-tlačidlovou navigáciou |
| Prehľad – účty | Vodorovne posuvný pruh všetkých nearchivovaných účtov; účty mimo súčtu sú stlmené |

## Dizajnové tokeny

Claude Code mockup na claude.ai neotvorí; záväzné sú tieto tokeny a PNG snímky obrazoviek v `docs/mockup/` v repozitári.

| Token | Tmavá téma | Použitie |
| --- | --- | --- |
| background | #0E1311 | Pozadie obrazovky |
| surface | #171E1B | Karty, riadky, spodná lišta |
| surfaceVariant | #202925 | Vnorené dlaždice v kartách |
| outline | #2A3530 | Oddeľovače, vypnutý prepínač, podklad ukazovateľov |
| onSurface | #EDF2EF | Hlavný text, výdavky |
| onSurfaceVariant | #A3B0A9 | Popisy, sekundárny text |
| primary | #6EE7B0 | Akcent, príjmy, aktívna záložka, hlavné tlačidlo |
| onPrimary | #0E1311 | Text na akcente |
| transfer / transferContainer | #9CC0FF / #24304A | Prevody |
| warning | #FFB36B | Prekročený rozpočet, deštruktívna akcia |

- Písma: Inter (text) a Space Grotesk (nadpisy, veľké sumy), obe pribalené v `res/font` (licencia OFL). Žiadne downloadable fonts, tie vyžadujú poskytovateľa mimo appky. Sumy používajú tabulkové číslice.
- Tvary: karty 24 / 18 / 14 dp, tlačidlá 16 dp, čipy a okrúhle tlačidlá plne zaoblené.
- Paleta kategórií (pozadie / text): zelená #2B3A33 / #6EE7B0, fialová #2E2B40 / #C5B8FF, oranžová #3A3226 / #FFB36B, modrá #24304A / #9CC0FF, ružová #3A2834 / #F5A3C7, sivá #2A3530 / #C9D4CE.
- Ikony sú vlastné vektorové drawable, bez knižnice `material-icons-extended`.
- Svetlú tému navrhne Claude Code v M6 ako druhú sadu tých istých tokenov s kontrastom textu ≥ 4,5 : 1 a predloží ju na schválenie.

## Biznis pravidlá

**Zostatky a súčty**

- Zostatok účtu = počiatočný stav + príjmy − výdavky + prevody na účet − prevody z účtu.
- „Spolu“ = súčet zostatkov nearchivovaných účtov s `includeInTotal = true`. Popis ukazuje „N z M účtov“.
- Prevody sa nepočítajú do príjmov, výdavkov, reportov ani rozpočtov.
- Reporty, rozpočty aj mesačné čísla na Prehľade počítajú transakcie zo všetkých účtov vrátane tých mimo súčtu.
- Dátum transakcie sa pri zadávaní nedá nastaviť do budúcnosti; budúce platby patria do opakovaných pravidiel. Výpočty podľa dnešného dátumu nefiltrujú, takže záznam, ktorý sa po zmene časového pásma ocitne o deň vpredu, sa počíta normálne.
- Formát súm: `1 234,56 €` (locale sk-SK). Výdavok má znamienko −, príjem +, prevod je bez znamienka. Pri filtri jedného účtu má prevod znamienko podľa smeru (− z účtu, + na účet).
- Oprava zostatku: používateľ zadá skutočný stav účtu a appka upraví `openingBalanceCents` o rozdiel. Nevzniká transakcia; reporty a rozpočty sa nemenia. Slúži aj na aktualizáciu hodnoty investičného účtu.

**Opakované platby**

1. Pri štarte appky a pri každom návrate do popredia sa pre každé nepozastavené pravidlo s `nextDate ≤ dnes` vytvorí transakcia s `date = nextDate`. Krok sa opakuje, kým `nextDate > dnes`, takže zmeškané platby sa dobehnú. Generátor sa spúšťa aj po uložení alebo obnovení pravidla a po importe zálohy. Ak niečo pribudlo, zobrazí sa snackbar „Zapísané opakované platby: N“ s akciou Zobraziť, ktorá otvorí Transakcie.
2. Perióda = `interval` × `frequency` (napríklad každé 3 mesiace). Výskyty tvoria rad `anchorDate` + k periód, počítaný vždy z `anchorDate`, nie z predchádzajúceho výskytu. Ďalší `nextDate` je prvý dátum z tohto radu, ktorý je neskorší než aktuálny `nextDate`. Platba z 31. dňa tak padne na posledný deň kratšieho mesiaca a potom späť na 31.
3. Vytvorenie transakcie a posun `nextDate` prebehnú v jednej DB transakcii a generátor beží pod jedným zámkom (Mutex), aby dvojitý beh nevytvoril duplicitu. Unikátny index na (`recurringRuleId`, `date`) sa nepoužíva, lebo používateľ môže dátum vygenerovanej transakcie zmeniť.
4. Vygenerovaná transakcia je bežná transakcia: dá sa upraviť aj zmazať bez vplyvu na pravidlo. Jej poznámka je názov pravidla. Duplikát transakcie nemá `recurringRuleId`.
5. Úprava pravidla mení len budúce výskyty. Zmena poľa „Ďalšia platba“, frekvencie alebo intervalu nastaví `anchorDate` na novú „Ďalšiu platbu“.
6. Nové pravidlo má predvolenú „Ďalšiu platbu“ dnes; prvá transakcia sa teda zapíše hneď po uložení a formulár to pri poli uvádza. Ak používateľ zvolí dátum v minulosti, appka pred uložením ukáže, koľko transakcií sa spätne vytvorí, a vyžiada potvrdenie.
7. Pravidlo sa dá uložiť len so sumou > 0, aktívnym účtom, kategóriou správneho druhu a pri prevode s dvoma rôznymi účtami.
8. Pozastavené pravidlo nič negeneruje. Po obnovení sa zmeškané výskyty nedobiehajú; `nextDate` skočí na prvý výskyt, ktorý je dnes alebo neskôr.
9. `endDate` je vrátane: výskyt v deň `endDate` sa ešte zapíše. Potom sa pravidlo presunie do skupiny „Ukončené“ a ostáva editovateľné.
10. Archivácia účtu alebo kategórie, na ktorú odkazuje aktívne pravidlo, toto pravidlo pozastaví a používateľa na to upozorní.
11. Súhrn „za mesiac“ na obrazovke Plán prepočíta každé aktívne pravidlo na mesačný ekvivalent (týždenné × 52 / 12, ročné / 12, všetko delené intervalom); prevody sa doň nepočítajú.

**Rozpočty**

- Rozpočet sa dá nastaviť len na hlavnú kategóriu. Čerpanie = súčet výdavkov kategórie a všetkých jej podkategórií v kalendárnom mesiaci. Nevyčerpaný zostatok sa do ďalšieho mesiaca neprenáša. Obrazovka ukazuje aktuálny mesiac.
- Limit je > 0. Kým čerpanie neprekročí limit (vrátane presne 100 %), je ukazovateľ v akcentovej farbe s textom „Zostáva X €“; nad limitom je oranžový s textom „Prekročené o X €“. Stav sa nikdy neodlišuje iba farbou.
- Súhrnná karta hore (mockup 08): zostatok = súčet limitov − súčet čerpania všetkých zobrazených rozpočtov (prekročenia sa započítajú); „ešte N dní“ = počet dní mesiaca − dnešný deň.
- Zmena limitu platí pre aktuálny a budúce mesiace; história limitov sa neukladá.
- Rozpočet archivovanej kategórie sa nezobrazuje. Pri zlúčení dvoch kategórií ostáva rozpočet cieľovej kategórie; ak cieľová rozpočet nemá a zdrojová áno, presunie sa na cieľovú.

**Podkategórie**

Mockupy podkategórie neukazujú; záväzný je tento text.

- Dve úrovne: hlavná kategória a podkategórie. Podkategória nemá vlastné podkategórie, má `kind` rodiča a zobrazuje sa v jeho farbe.
- Transakcia aj opakované pravidlo môžu byť zaradené priamo do hlavnej kategórie alebo do jej podkategórie. Podkategória je vždy nepovinná.
- Zobrazenie v riadkoch: „Auto · Tankovanie“; písmeno a farba ikony sú z hlavnej kategórie.
- Filter kategórie: výber hlavnej kategórie zahŕňa aj jej podkategórie; dá sa vybrať aj jedna podkategória.
- Reporty a rozpočty počítajú podkategórie do hlavnej kategórie. V rozpise podkategórií v Reportoch je aj riadok „bez podkategórie“, ak má hlavná kategória priame transakcie; ťuknutie na riadok rozpisu otvorí jeho transakcie.
- Predvolené kategórie v sprievodcovi (každá položka sa dá odškrtnúť; aspoň jedna hlavná z každého druhu):
  - Výdavkové: Potraviny · Bývanie (Nájom, Energie, Internet) · Auto (Tankovanie, Servis, Poistenie) · Deti · Zábava · Zdravie (Lekár, Lieky) · Oblečenie · Ostatné
  - Príjmové: Výplata · Ostatné príjmy
- Vlastné kategórie a podkategórie si používateľ vytvára v Nastavenia → Kategórie (M5): vytvorenie, premenovanie, zmena poradia v rámci rodiča, presun podkategórie pod inú hlavnú kategóriu rovnakého druhu, povýšenie podkategórie na hlavnú a zaradenie hlavnej kategórie bez podkategórií pod inú.
- Zlúčenie: len rovnaký `kind`. Zdrojová kategória sa zmaže, jej transakcie a pravidlá prejdú na cieľovú. Podkategória sa dá zlúčiť do svojej hlavnej kategórie. Pri zlúčení hlavnej kategórie s podkategóriami sa jej podkategórie presunú pod cieľovú.
- Archivácia hlavnej kategórie skryje aj jej podkategórie (ich vlastný príznak sa nemení, takže obnovenie vráti pôvodný stav). Pravidlo 10 opakovaných platieb platí aj pre pravidlá zaradené v podkategóriách.
- Posledná aktívna hlavná kategória daného druhu sa nedá archivovať.
- Záloha: import overí, že rodič existuje, je hlavnou kategóriou a má rovnaký `kind`.

## Stack a architektúra

Natívny Android v Kotline s Jetpack Compose, jeden modul `app`, žiadne proprietárne závislosti. Verzie knižníc: aktuálne stabilné v čase založenia projektu, zapísané vo version catalogu (`libs.versions.toml`).

| Oblasť | Voľba | Dôvod |
| --- | --- | --- |
| UI | Jetpack Compose + Material 3, paleta podľa sekcie Dizajnové tokeny, tmavá aj svetlá téma | Moderný vzhľad bez XML layoutov |
| Navigácia | Navigation Compose, jedna Activity | Jednoduché deep linky pre widget |
| Dáta | Room (KSP) + Kotlin Flow | Reaktívne zoznamy a súčty |
| Nastavenia | Preferences DataStore | Bez SharedPreferences |
| Stav | ViewModel + StateFlow (MVVM) | Štandard, dobre testovateľné |
| DI | Ručný `AppContainer` | Appka je malá; menej build závislostí než Hilt |
| Plánovanie | Kontrola pri štarte a pri návrate appky do popredia, bez WorkManagera | Zostatky sa čítajú len v appke; menej závislostí a povolení |
| Widget | Klasický AppWidgetProvider + RemoteViews | Jedno tlačidlo nepotrebuje Glance ani jeho závislosti |
| Zámok | androidx.biometric `BiometricPrompt` | Biometria aj PIN zariadenia |
| Serializácia zálohy | kotlinx.serialization (JSON) | Čitateľný, verzovaný formát |
| Grafy | Vlastné Compose komponenty (pruhy) | Žiadna grafová knižnica |

**Štruktúra balíčkov**

- `data` – Room entity, DAO, repozitáre, záloha
- `domain` – výpočet zostatkov, generátor opakovaní, výpočet rozpočtov (čistý Kotlin, bez Android API)
- `ui` – obrazovky, ViewModely, téma, spoločné komponenty
- `widget` – AppWidgetProvider s jedným tlačidlom

**Obmedzenia buildu**

- Žiadne Google Play Services, Firebase ani iné uzavreté SDK; build musí byť kompatibilný s F-Droid.
- Gradle Kotlin DSL, `minSdk 34`, `targetSdk` aktuálne stabilné.
- `applicationId` sa určí v M1 a už sa nemení.
- Podpisovanie: jeden stály keystore mimo repozitára, použitý pre každý build inštalovaný na telefón. Build podpísaný iným kľúčom sa cez existujúcu appku nenainštaluje a odinštalovanie zmaže dáta, preto pred inštaláciou nového buildu vždy export zálohy.
- Testy bežia na JVM bez emulátora (JUnit, Robolectric pre Room a DAO); UI sa overuje ručne podľa zoznamu po každom míľniku.
- Release build s R8. V logoch nikdy sumy, poznámky ani heslo zálohy.
- Sumy výhradne `Long` v centoch; formátovanie na jednom mieste (`MoneyFormatter`).
- Dynamické farby (Material You) vypnuté, aby vzhľad zodpovedal tokenom.
- Ak to stabilná verzia androidx.biometric vyžaduje, jediná Activity dedí z `FragmentActivity`.

## Bezpečnosť a záloha

Dáta neopustia zariadenie inak než ako šifrovaný súbor, ktorý používateľ sám exportuje.

**Povolenia a uloženie**

- Zlúčený manifest neobsahuje žiadne sieťové povolenie (`INTERNET`, `ACCESS_NETWORK_STATE`). Povolenia, ktoré pridávajú knižnice AndroidX (biometria, interné signature povolenie androidx.core), sú v poriadku.
- `android:allowBackup="false"` a prázdne pravidlá extrakcie dát, aby databáza nešla do systémových záloh. Dôsledok: jedinou kópiou dát mimo telefónu je manuálny export.
- Databáza je v súkromnom úložisku appky; šifrovanie v pokoji zabezpečuje systém (file-based encryption). SQLCipher sa nepoužíva.
- `FLAG_SECURE` na okne, keď je zámok zapnutý (žiadny náhľad v prehľade úloh, žiadne screenshoty).

**Zámok appky**

- Voliteľný, v Nastaveniach. `BiometricPrompt` s `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`.
- Zapnúť sa dá len vtedy, keď `BiometricManager.canAuthenticate` vráti úspech. Ak používateľ neskôr zruší zámok obrazovky zariadenia, appka sa otvorí bez overenia a zobrazí upozornenie, aby sa z nej nevymkol.
- Zamyká sa pri studenom štarte a po 60 s na pozadí. Platí aj pre otvorenie cez widget. Pred úspešným overením sa nevykreslí žiadny obsah appky.
- Systémový dialóg výberu súboru pri exporte a importe sa nepočíta ako pobyt na pozadí, aby zámok neprerušil rozpracovanú zálohu.
- Zámok je brána v UI, nie šifrovanie databázy; v README to má byť uvedené.

**Export**

1. Nastavenia → Záloha → Exportovať; používateľ zadá heslo zálohy (min. 10 znakov, dvakrát). Heslo sa neukladá a appka upozorní, že bez neho sa záloha nedá obnoviť.
2. Všetkých 5 tabuliek sa serializuje do JSON s poľom `schemaVersion`, potom gzip. Nastavenia appky v zálohe nie sú. Serializácia aj šifrovanie prebehnú v pamäti; nešifrovaný obsah sa nikdy nezapisuje na disk.
3. Šifrovanie AES-256-GCM s náhodným 12-bajtovým nonce; kľúč z hesla cez PBKDF2-HMAC-SHA256 so 600 000 iteráciami a náhodnou 16-bajtovou soľou. Hlavička súboru (magic `GROS`, verzia formátu, počet iterácií, soľ, nonce) vstupuje do šifrovania ako overené dáta (AAD).
4. Cieľ vyberie používateľ cez systémový dialóg (`ACTION_CREATE_DOCUMENT`), predvolený názov `gros-RRRR-MM-DD.grosbak`.
5. Nastavenia zobrazia „Posledná záloha: dátum“. Ak je záloha staršia než 30 dní alebo žiadna, Prehľad ukáže nevtieravý pruh „Zálohovať“; pruh sa dá v Nastaveniach vypnúť.

**Import**

1. Výber súboru (`ACTION_OPEN_DOCUMENT`) a heslo. Nesprávne heslo alebo poškodený súbor = zrozumiteľná chyba, dáta ostávajú nedotknuté.
2. Obsah sa pred zápisom zvaliduje (referencie medzi tabuľkami, invarianty); nevalidná záloha sa odmietne. Staršia `schemaVersion` sa zmigruje, novšia sa odmietne.
3. Potvrdenie s počtom účtov a transakcií v zálohe a upozornením, že import nahradí všetky súčasné dáta a že pri staršej zálohe sa dobehnú zmeškané opakované platby.
4. Náhrada prebehne v jednej DB transakcii. Potom sa prepočíta `noteSearch`, vynuluje posledný použitý účet a spustí generátor opakovaní.

Import je dostupný aj v sprievodcovi prvým spustením (obnova na novom telefóne).

## Míľniky a akceptačné kritériá

Šesť míľnikov; každý končí zelenými testami, inštalovateľným APK podpísaným stálym kľúčom a commitom.

| # | Míľnik | Hotové, keď |
| --- | --- | --- |
| M1 | Kostra a dáta: projekt, podpisovanie, téma z tokenov, Room schéma, repozitáre, výpočet zostatkov | Unit testy zostatkov a invariantov prechádzajú |
| M2 | Účty a transakcie: sprievodca (predvolené kategórie a podkategórie), Účty, Nová/úprava transakcie s výberom podkategórie a našepkávaním poznámky, zoznam s filtrami, hľadaním a súčtom, Prehľad | Výdavok, príjem aj prevod sa dajú vytvoriť, upraviť a zmazať |
| M3 | Opakované platby: CRUD pravidiel a generátor | Testy generátora prechádzajú (nižšie) |
| M4 | Rozpočty a Reporty | Čísla sedia s ručným výpočtom na testovacích dátach |
| M5 | Správa kategórií a podkategórií, zámok, záloha (aj import v sprievodcovi), widget, Nastavenia | Export → vymazanie dát → import vráti identické dáta |
| M6 | Doladenie: svetlá téma a prepínač témy, prázdne stavy, responzivita, prístupnosť, README, LICENSE | Kontrolný zoznam nižšie je celý splnený |

**Akceptačné kritériá**

- [ ] Zápis výdavku: + → suma → kategória → Uložiť, bez povinného ďalšieho poľa.
- [ ] Každý typ záznamu (transakcia, prevod, pravidlo, účet, kategória, rozpočet) má funkčnú úpravu.
- [ ] Rozpracovaná transakcia prežije otočenie obrazovky.
- [ ] Posledný aktívny účet sa nedá archivovať; pri jednom účte je typ Prevod nedostupný.
- [ ] Výdavok sa dá uložiť do hlavnej kategórie aj do podkategórie; výber podkategórie nie je povinný.
- [ ] Rozpočet, report aj filter hlavnej kategórie zahŕňajú transakcie jej podkategórií.
- [ ] Kategórie nemajú tretiu úroveň; podkategória má vždy `kind` rodiča (unit test invariantov).
- [ ] Generátor opakovaní: mesačné pravidlo z 31. dňa, 29. 2. v neprestupnom roku, interval 3 mesiace, dobehnutie 3 zmeškaných mesiacov, výskyt v deň `endDate`, dvojitý beh bez duplicít.
- [ ] Zmena dátumu alebo zmazanie vygenerovanej transakcie nezablokuje ani nezopakuje ďalší výskyt pravidla.
- [ ] Prevod nemení príjmy, výdavky, reporty ani rozpočty; mení len zostatky oboch účtov.
- [ ] Účet mimo súčtu sa neprejaví v „Spolu“, ale jeho výdavky sú v reportoch.
- [ ] Oprava zostatku zmení zostatok účtu na zadanú hodnotu a nezmení reporty ani rozpočty.
- [ ] Hľadanie „alza“ nájde transakcie s poznámkou „Alza“ na všetkých účtoch a súčet nad výsledkom sedí s ručným výpočtom.
- [ ] Ťuknutie na účet zobrazí len jeho pohyby vrátane prevodov a jeho aktuálny zostatok.
- [ ] Záloha: round-trip test, nesprávne heslo, poškodený súbor, nevalidný obsah, odmietnutie novšej verzie schémy.
- [ ] So zapnutým zámkom sa pred overením nevykreslí žiadny obsah; bez zámku obrazovky zariadenia sa appka otvorí s upozornením.
- [ ] Zlúčený manifest release buildu neobsahuje `INTERNET` ani `ACCESS_NETWORK_STATE` (automatický test nad výstupom buildu).
- [ ] Každá obrazovka je použiteľná pri šírke 360 dp, písme 200 % a na šírku: nič nie je orezané ani prekryté.
- [ ] Dotykové ciele ≥ 48 dp, kontrast textu ≥ 4,5 : 1, popisy pre TalkBack na ikonách.
- [ ] Žiadna funkcia zo zoznamu „Zámerne mimo rozsahu“ nie je implementovaná.

**Pokyny pre Claude Code**

1. Pred M1 prečítaj celú špecifikáciu a snímky v `docs/mockup/` a vypíš rozpory, nejasnosti a riziká. Kód začni písať až po ich vyriešení.
2. Pracuj po míľnikoch v poradí M1 → M6; pred každým napíš krátky plán a počkaj na schválenie.
3. Logiku v `domain` píš najprv testami (JUnit), až potom UI.
4. Nič mimo tejto špecifikácie nepridávaj; nejasnosť = otázka, nie domýšľanie.
5. Po každom míľniku spusti testy a lint, zostav podpísané APK a zhrň, čo sa dá ručne overiť na telefóne.

## Rozhodnutia z kroku 1 (16. 11. 2026)

Schválené Mirecom. Pri konflikte s mockupom alebo so starším textom vyššie platí táto sekcia.

**Rozpory mockup ↔ špecifikácia**

- A1 Súhrn nad zoznamom transakcií: vždy riadok s počtom a obe sumy (príjmy, výdavky).
- A2 Vygenerovaná transakcia má poznámku = názov pravidla a navyše štítok „opakovaná“, kým má `recurringRuleId`.
- A3 Titulok riadku je kategória; v režime hľadania je titulkom poznámka (mockup 03).
- A4 Filter obdobia: Mesiac / Rok / Celé obdobie, bez vlastného rozsahu. Predvolený je aktuálny mesiac. Hľadanie, ťuknutie na účet a akcia „Zobraziť“ zo snackbaru opakovaných platieb prepnú na Celé obdobie; z Reportov sa prenesie obdobie reportu.
- A5 Filter kategórie aj účtu je výber jednej položky.
- A6 Rozpočet je oranžový až pri čerpaní nad limit (zapracované v sekcii Rozpočty).
- A7 Súhrnná karta rozpočtov podľa mockupu 08 (zapracované v sekcii Rozpočty).
- A8 Hlavička dňa ukazuje príjmy − výdavky dňa, bez prevodov.
- A9 Zákaz budúceho dátumu transakcie je pravidlo formulára, nie invariant repozitára.
- A10 Do M5 existujú len kategórie zo sprievodcu; prepínač témy príde v M6; import v sprievodcovi sa doplní v M5.
- A11 „Mesačne, 1. deň“ je odvodený popis z „Ďalšej platby“; vyberá sa len frekvencia a interval.
- A12 Znamienka súhrnov podľa mockupu danej obrazovky; predložka z/zo podľa čísla („3 zo 4 účtov“, „2 z 5 účtov“).

**Nejasnosti**

- B1 `applicationId` = `io.github.mstajanc.gros`.
- B2 Predvolené kategórie: pozri Biznis pravidlá – Podkategórie.
- B3 Posledná aktívna hlavná kategória druhu sa nedá archivovať.
- B4 Archivované účty a kategórie sú v sekcii „Archivované“ na konci zoznamu s akciou Obnoviť. Účet s nenulovým zostatkom sa dá archivovať po potvrdení; jeho transakcie ostávajú v reportoch.
- B5 Zlúčenie kategórií: pozri Podkategórie a Rozpočty.
- B6 Duplikovať otvorí predvyplnený formulár s dnešným dátumom; uloží sa až tlačidlom Uložiť.
- B7 Späť po uložení úpravy vráti pôvodné hodnoty; po uložení novej transakcie ju zmaže.
- B8 Sumy mimo Novej transakcie (pravidlo, limit, počiatočný stav, oprava zostatku) sa zadávajú rovnakou klávesnicou appky v spodnom paneli. Počiatočný stav a oprava môžu byť záporné. Maximum je 9 999 999,99 €.
- B9 Našepkávanie hľadá cez `noteSearch`, zoskupuje podľa presného textu poznámky, pri rovnakom počte použití vyhrá novšia; započíta aj poznámky z opakovaných platieb.
- B10 Zoznam pravidiel má skupiny Aktívne / Pozastavené / Ukončené, v skupine podľa `nextDate`. Názov pravidla je povinný.
- B11 Predĺženie alebo zrušenie `endDate` na ukončenom pravidle zmeškané výskyty nedobieha (`nextDate` skočí na prvý výskyt dnes alebo neskôr). Posun „Ďalšej platby“ do minulosti pri úprave vyžiada rovnaké potvrdenie ako pri novom pravidle.
- B12 Bez akejkoľvek zálohy sa 30 dní pripomienky počíta od prvého spustenia.
- B13 Reporty: pruhy sú škálované voči najväčšej kategórii, zobrazia sa všetky hlavné kategórie, šípka za aktuálny mesiac je vypnutá.
- B14 Chybové stavy používajú farbu `warning`.
- B15 Ikony: cesty zo sady Lucide (licencia ISC) ako vektorové drawable, licencia priložená v repozitári.
- B16 Zmena poradia potiahnutím je vlastná implementácia bez knižnice; pre TalkBack akcie „posunúť vyššie“ a „posunúť nižšie“.
- B17 Schéma v1 (všetkých 5 tabuliek vrátane `Category.parentId`) sa zmrazí na konci M1; každá neskoršia zmena má migráciu a test.

**Implementačné poznámky**

- `MoneyFormatter` je ručne písaný (nezávislý od `NumberFormat` a jazyka zariadenia): nedeliteľná medzera v tisícoch a pred €, desatinná čiarka, znamienko „−“ (U+2212). Dátumy sa formátujú vždy v slovenčine.
- Písma Inter a Space Grotesk sa stiahnu z ich oficiálnych repozitárov; pred stiahnutím uviesť súbor, zdroj a veľkosť.
- Keystore vytvára Mirec sám príkazom `keytool`; heslo nikdy nie je v repozitári ani v chate, cesta a údaje sú v súbore mimo gitu.
- V PATH nie je `java`; build používa JDK z Android Studia (`C:\Program Files\Android\Android Studio\jbr`). Verzie knižníc sa overia online na začiatku M1.
- Obrazovky bez mockupu (sprievodca, úprava účtu, výbery filtrov, zoznam „Viac“, podkategórie, poznámka s našepkávaním, formulár rozpočtu, ročný report, Nastavenia, kategórie, záloha, zámok, widget, svetlá téma) sa popíšu v pláne príslušného míľnika.

## Rozhodnutia po teste M2 (17. 11. 2026)

Schválené Mirecom. Majú prednosť pred mockupmi aj starším textom.

- C1 Hľadanie v Transakciách nájde text v poznámke aj v názve kategórie alebo jej hlavnej kategórie („tank“ nájde všetky Auto · Tankovanie), bez ohľadu na veľkosť písmen a diakritiku.
- C2 Kategórie vo formulári transakcie sú zaoblené čipy široké podľa názvu, v riadkoch za sebou, text vždy na jeden riadok; ostáva prvých 7 hlavných kategórií a „Viac“.
- C3 Číselná klávesnica: pri novej transakcii je otvorená a skryje sa po výbere kategórie (ak je suma zadaná) alebo po ťuknutí na iné pole; pri úprave a duplikáte je skrytá. Ťuknutie na sumu ju vždy otvorí. Uložiť je stále prichytené dole.
- C4 Správa kategórií a podkategórií ostáva v M5 (rozhodnutie A10 platí).
