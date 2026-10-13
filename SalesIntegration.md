# Prodaja i Konty / Allegra integracija

Istraživanje: 11. oktobar 2026. Status: priprema implementacije; mogućnost slanja prodaje iz Mega Bike aplikacije još nije potvrđena od dobavljača.

Ažuriranje, 12. oktobar 2026: korisnik je kontaktirao dobavljača i dobio najavu da će API biti dostupan do kraja godine. Razvoj prodaje i adaptera se odlaže; trenutni fokus je uvoz/prijem robe i nova kategorizacija. Dokumentacija i testni pristup API-ju još nisu dostavljeni.

## Postojeća osnova

- `Catalog.md`, odeljak 11, već predviđa poseban Allegra adapter i navodi otvorena pitanja o API-ju, cenama, barkodovima i zalihama. To je arhitektonski plan, ne završena integracija.
- `Initial.md` predviđa modul `sales`. U trenutnom backendu taj modul ne postoji.
- `Product` sadrži jedinstveni opcioni `barcode`, šifru, eksterni ID, cenu, PDV, jedinicu mere i aktivnost. `ProductRepository.findByBarcode` postoji; javna pretraga proizvoda je tekstualna i ne predstavlja poseban endpoint za tačno skenirani barkod.
- Inventar već vodi stanje po lokaciji i istoriju promena. Prodaja mora dobiti sopstvenu referencu u toj istoriji i zaštitu od višestrukog skidanja istog iznosa zaliha.
- Trenutno postoji jedan barkod po proizvodu. Više proizvođačkih i internih barkodova po artiklu zahteva proširenje modela.

## Provereni nalazi i ograničenja

Konty je naslednik Arie i Allegre; lokalni [Allegra sajt](https://allegrapos.rs/) potvrđuje promenu brenda. Mora se utvrditi koja aplikacija i verzija stvarno rade u prodavnici; stare i nove instalacije ne treba unapred tretirati kao isti integracioni protokol.

[Konty integracije](https://konty.com/rs/integrations) navode integracije u Premium paketu, fiskalizaciju preko Symphony LPFR-a ili V-PFR-a i uvoz/izvoz artikala kroz Excel/CSV. Ovo ne potvrđuje da postoji API kojim Mega Bike može otvoriti i fiskalizovati račun. Uvoz artikala nije protokol za naplatu.

[Tehničke reference](https://docs.konty.com/technical_references.html) opisuju komunikaciju Konty ESIR-a sa PFR-om, čuvanje originalnog JSON odgovora, poreske oznake dobijene od PFR-a i periferije. To nije dokumentacija za eksterni Konty API. Direktna veza našeg softvera sa PFR-om bila bi zaseban ESIR projekat, čije uslove treba proveriti pre takve odluke.

[Prodaja u trgovini](https://docs.konty.com/sale_interface/sales.html) opisuje korisnički tok izbora artikala i naplate. Dokumentacija je korisna za razumevanje rada kase, ali ne određuje ugovor za naš adapter.

U pregledanim javnim izvorima nije pronađena specifikacija eksternog API-ja za kreiranje računa, autentifikaciju, preuzimanje statusa ili obaveštavanje o završetku. API može postojati kroz partnersku podršku. Polazni URL `https://konty.com/rs/aria-allegra` prvobitno nije bio dostupan alatu za pregled; naknadno je otvoren preko preusmerenja sa `https://allegrapos.rs/`.

## Naknadna provera verzija i postojeće licence

Korisnik potvrđuje stavku računa „Allegra POS mesečna licenca (Win + tehnička podrška)“ i postojeću prodaju iz aplikacije povezane sa fizičkom kasom. To opisuje licencu i tok rada, ali ne utvrđuje instaliranu verziju, model fiskalne opreme ili dostupnost integracionog API-ja.

Javno potvrđeni unosi u registru Poreske uprave:

| Naziv | Registrovana verzija | IB elementa | Datum odobrenja | Izvor |
| --- | --- | --- | --- | --- |
| Allegra | 6.0.0 | 324 | 29.12.2021. | [Registar, strana 1](https://purs.gov.rs/eFiskalizacija/registar-odobrenih-elemenata-efu.html?position=1) |
| Allegra | 7.0.0 | 793 | 08.04.2022. | [Registar, strana 7](https://www.purs.gov.rs/lat/eFiskalizacija/registar-odobrenih-elemenata-efu.html?position=7) |
| Konty | 8 | 1442 | 09.05.2025. | [Registar, strana 17](https://purs.gov.rs/eFiskalizacija/registar-odobrenih-elemenata-efu.html?position=17) |

Ovo su registrovane ESIR verzije, ne potpun spisak Windows izdanja ili ažuriranja. Nijedna nije potvrđena kao verzija u prodavnici. Konty tehničke reference navode prikaz verzije i serijskog broja u donjem desnom uglu stranice za prijavu; za staru Allegru proveriti početni ekran ili tražiti podatak od podrške.

Dodatno su pretraženi Allegra/Konty API/SDK pojmovi i javno [Allegra ESIR uputstvo](https://allegrapos.rs/files/Allegra%20ESIR.pdf); nije pronađen javni ugovor za eksterno izdavanje računa. Rezultat `developer.alegra.com` odnosi se na drugi, latinoamerički proizvod Alegra i ne sme se koristiti za ovu integraciju.

## Predlog prve verzije stranice Prodaja

1. Izbor prodajnog mesta/lokacije i prikaz kasira.
2. Polje za skeniranje barkoda, kao i ručna pretraga po šifri i nazivu. Tačno skenirani barkod dodaje jedan artikal; ponovno skeniranje povećava količinu. Nepoznat barkod prikazuje jasnu poruku.
3. Korpa sa količinama, cenom sa PDV-om, dozvoljenim popustom, raspoloživim zalihama i ukupnim iznosom. Backend računa i proverava iznose.
4. Čuvanje i ponovno otvaranje nacrta prodaje.
5. Izbor načina plaćanja i, ako je potrebno, identifikacije kupca. Kartično plaćanje u evidenciji ne znači automatsko upravljanje bankarskim terminalom.
6. Slanje kasi kroz dokumentovani adapter, prikaz statusa i vezivanje potvrđenog fiskalnog računa za prodaju.
7. Istorija prodaja, povrat/refundacija i ponovna štampa već izdatog računa kroz podržani protokol.

Bez potvrđenog adaptera prva verzija može čuvati nacrte i pripremati prodaju. Ne sme prikazivati testnu potvrdu kao završen fiskalni račun.

## Pravila pouzdanosti

- Čuvati snimak naziva, šifre, barkoda, cene, popusta i poreza na stavci prodaje, nezavisno od kasnijih izmena kataloga.
- Posebno voditi stanje naplate, fiskalizacije i štampe. Neuspešna štampa nakon uspešne fiskalizacije ne sme napraviti novi račun.
- Čuvati internu referencu prodaje i referencu eksternog zahteva pre slanja; podrška za jedinstveni ključ zahteva mora se proveriti kod dobavljača.
- Ako odgovor izostane, stanje je nepoznato. Proveriti status po referenci pre ponovnog slanja; bez podržane provere predvideti ručno usaglašavanje, bez automatskog ponavljanja fiskalizacije.
- Rezervisati robu pre slanja i potvrditi promenu zaliha tačno jednom nakon potvrđenog ishoda. Rezervaciju sa nepoznatim fiskalnim ishodom ne oslobađati naslepo. Naknadno knjiženje mora moći da se oporavi nakon pada aplikacije.
- Dogovoriti ko upravlja zalihama i cenama. Ako se prodaje direktno u Konty-ju, i te prodaje/povrati moraju stići u Mega Bike.
- Oblik adaptera odrediti tek po dokumentaciji: cloud API, lokalni servis ili drugi zvanično podržani mehanizam. Udaljeni backend ne može pristupiti `localhost` servisu na računaru kase.

## Barkod skeniranje i etikete

Predlog je prvo podržati USB/Bluetooth skener u režimu tastature, sa završnim Enter. To treba proveriti na stvarnom modelu. Skeniranje treba usmeriti na namensko polje i ne ometati unos količine ili cene.

Barkod ostaje tekst kako se ne bi gubile početne nule. Uvesti tačno prepoznavanje, proveru jedinstvenosti i jasnu poruku za neaktivan artikal. Ponavljanje stvarnog skeniranja povećava količinu; ponavljanje mrežnog zahteva ne sme duplirati unos.

Za robu sa postojećim barkodom koristiti taj kod. Za interne etikete razmotriti Code 128 sa internom šifrom, nakon provere kompatibilnosti skenera i kase. Ne izmišljati globalne GTIN/EAN identifikatore; ako budu potrebni, dogovoriti pravila dodele i validaciju.

Štampa etiketa je odvojena od štampe fiskalnih računa. Potrebni su model štampača, veza, rezolucija, dimenzije etikete, broj etiketa u redu i željeni sadržaj: naziv, šifra, barkod, eventualno cena. Početna štampa preko PDF-a/pregledača i automatska štampa preko lokalnog servisa imaju različite zahteve. Štampana etiketa mora proći fizički test skeniranja.

## Podaci potrebni od prodavnice

- Tačan program, verzija, postojeći paket/licenca i kontakt instalatera/podrške.
- Operativni sistem i model računara/tableta kase; broj prodajnih mesta i kasa.
- LPFR/V-PFR proizvod i verzija, model štampača računa, lokalna mrežna postavka i mesto na kome će raditi Mega Bike backend.
- Izvoz nekoliko artikala sa šiframa, barkodovima, cenama, jedinicama mere i poreskim oznakama, radi mapiranja.
- Dogovor o tome gde se menjaju cene, gde se vode zalihe i da li će zaposleni nastaviti prodaju i direktno u Konty-ju.
- Načini plaćanja, potreba za mešovitim plaćanjem, kupcima sa PIB-om, popustima i povratima.
- Modeli skenera i štampača etiketa, dimenzije etiketa i primer željenog sadržaja.

PIB i identifikatori firme, lokacije i kase biće potrebni pri konfiguraciji integracije. Produkcione lozinke, PIN bezbednosnog elementa i privatne ključeve ne čuvati u ovom dokumentu ili repozitorijumu; od dobavljača tražiti namenski testni pristup.

## Tekst upita za Konty / Digitron ist podršku

> Razvijamo Mega Bike aplikaciju za katalog, prijem robe, zalihe i prodaju. Želimo da kasir u našoj web aplikaciji skenira artikle i pripremi prodaju, a postojeći Allegra/Konty sistem izda i odštampa fiskalni račun.
>
> Da li za našu verziju i paket postoji zvanično podržan API/SDK ili lokalni servis za kreiranje i fiskalizaciju računa iz eksternog sistema? Molimo dokumentaciju, način autentifikacije, uslove licence i testno okruženje.
>
> Potrebni su nam: mapiranje/sinhronizacija artikala, barkodova, cena i poreskih oznaka; slanje stavki, popusta, kupca i načina plaćanja; povrat broja i statusa fiskalnog računa; provera statusa po našoj jedinstvenoj referenci nakon prekida veze; zaštita od duplog izdavanja; refundacija i ponovna štampa. Da li postoji webhook ili preuzimanje prodaja i povrata nastalih direktno na kasi?
>
> Molimo i opis mrežne postavke (cloud ili lokalni servis), rada bez interneta i podrške za skener i štampač barkod etiketa.

Kontakt: [Konty podrška](https://konty.com/rs/contact), `contact@konty.com`, +381648207513. Postojeći instalater je najbolji prvi kontakt za konkretnu instalaciju.

## Redosled implementacije

1. Razviti prodajni domen, nacrte, tačno barkod prepoznavanje i stranicu Prodaja uz postojeći katalog/inventar.
2. Uvesti generisanje internih kodova i šablon etiketa prema potvrđenoj opremi.
3. Po dobijanju protokola napraviti adapter i mapiranje artikala/poreskih oznaka za konkretnu kasu.
4. U testnom okruženju proveriti izdavanje, prekid veze nakon slanja, ponovno kliktanje na naplatu, neuspeh štampe, oporavak knjiženja zaliha i povrat.
5. Na stvarnoj opremi proveriti skeniranje i štampu, zatim konfigurisati produkcioni pristup.
