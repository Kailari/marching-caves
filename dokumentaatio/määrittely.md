Marching Caves
==============
Tarkoituksena on toteuttaa ohjelma, joka proseduraalisesti generoi yksinkertaisen luolan. Luola generoidaan kolmessa vaiheessa:
 1. Generoidaan polku *(yksinkertainen verkko)*
 2. Määritellään *(polkua käyttäen)* ympäröivän avaruuden aineelle tiheysfunktio
 3. Valitaan tiheydelle kynnysarvo ja generoidaan tästä *isosurface*
 
Ohjelmalle annetaan syötteenä siemenluku, jonka pohjalta se generoi deterministisesti luolan em. vaiheiden mukaisesti.


Algoritmin yleiskuvaus
----------------------

### Polun generointi
Polun generointiin käytetään *Random Walk* -algoritmia mukailevaa lähestymistapaa. Tällä saadaan suhteellisen kevyesti ja yksinkertaisesti generoitua dataa muita vaiheita varten.

Polkua aletaan generoida jostain satunnaisesta pisteestä, ja tästä lähdetään ottamaan satunnaiseen suuntaan ennaltamäärätyn mittaisia askeleita. Mikäli polun halutaan haarautuvan, voidaan myöhemmin palata satunnaiseen polun pisteeseen ja aloittaa uusi kävely siitä.

### Tiheysfunktio
Isosurfacen generointia varten meidän tulee voida lukea avaruudesta valitulle mielivaltaiselle pisteelle tiheysarvo. Tätä varten tulee määrittää tiheysfunktio
```
f(x, y, z) -> t
```
jossa `t` on aineen tiheys pisteessä `(x, y, z)`.

Itse tiheysfunktion määrittelyn pohjana käytetään generoitua polkua. Yksinkertaisimmillaan, aineen tiheys voidaan määrittää funktiona pisteen etäisyyden lähimpään polun kaareen suhteen. Siis esimerkiksi
```
d(x, y, z) = "pisteen (x, y, z) etäisyys polusta"

f(x, y, z) = 1 / d(x, y, z)
```

Määrittämällä sitten jokin kynnysarvo `K`, voidaan tiheydet rajata kiinteään aineeseen *(eli kun `f(x,y,z) > K`)* ja tyhjään tilaan *(eli kun `f(x,y,z) <= K`)*.

Esimerkin mukainen tiheysfunktio tuottaa pyöreän tunnelin, polun muotoa mukaillen. Tätä voidaan muuntaa mielenkiintoisemmaksi esimerkiksi, approksimoimalla pinnan normaaleja pyöreän tunnelin pinnan normaaleilla *(suuntavektori pisteestä `(x, y, z)` polun lähimpään pisteeseen)* ja käyttämällä näitä etäisyys- ja tiheysfunktioiden vääristämiseen. 

Tämän päälle voidaan kerrostaa erilaisia kohinafunktioita erilaisilla painoarvoilla mielenkiintoisempien muotojen aikaansaamiseksi. Esimerkiksi pisteen tiheyteen voisi vaikuttaa etäisyysfunktiosta riippumaton kolmiuloitteinen *Simplex Noise*. Lisäksi normaaleja käyttäen luolan "seiniin", "lattiaan" ja "kattoon" voidaan sovittaa erilaisia kohinafunktioita.

Toisaalta, mikäli polku kulkee jossain kohdin hyvin läheltä itseään *(eli tekee esim. U-käännöksen)*, ei lyhimmän etäisyyden laskeminen välttämättä riitä. Tämän vuoksi tiheys tulee oikeastaan laskea polun kaarien *etäisyyden mukaan painoitettujen kontribuutioiden summana*, mutta itse laskenta pysyy hyvin samankaltaisena.

Eli, TL;DR:
 1. Määritetään mielivaltaisen avaruuden pisteen tiheysfunktio `f(x, y, z)` pisteen lyhimmän etäisyyden polun kaariin suhteen.
 2. Lisätään etäisyydestä laskettuun tiheyteen "globaali" tiheyskomponentti. Tämän generointiin käytetään *Simplex Noise* -pohjaista (fraktaali-) kohinafunktiota.
 3. Kerrostetaan vielä kohinafunktioita, kuten *Worley Noise*, *Simplex Noise* ym. osana tiheysfunktiota. Näille voidaan antaa painoarvoja pisteen ja lähimmän polun kaaren välisen suuntavektorin *("pinnan normaalin approksimaatio")* mukaan, jolloin esim. lattia voidaan tehdä eri näköiseksi kuin seinät ja katto.
 4. Toistetaan vaihe 3. kaikille *(kyllin lähellä oleville)* kaarille todellisen kontribuutioiden summan laskemiseksi. *(Painottaen summan osatekijöitä etäisyyden mukaan)*

 
### Isosurfacen generointi
Kun tiheysfunktio on määritelty, voidaan avaruudesta valita mielivaltainen piste ja laskea sen tiheys. Tätä voidaan käyttää nk. isosurfacen laskemiseen. Kuten edellisessä osiossa mainittiin, määrittämällä kynnysarvo `K`, voidaan tiheydet rajata kiinteään aineeseen ja tyhjään tilaan. Ongelmana on löytää avaruudesta kiinteän aineen ja tyhjän tilan rajakohdat.

Tätä prosessia jossa rajakohdat etsitään kutsutaan isosurfacen generoinniksi, ja tarkoituksena on käyttää *Marching Cubes* -algoritmia ongelman ratkaisuun. Kyseessä on *Marching Cubes* -algoritmista johdettu muunnelma joka ratkaisee joitain alkuperäisen algoritmin tuottaman mallin topologian monikäsitteisyysongelmia.

Algoritmin ideana on siis lukea avaruutta kolmiuloitteisena ruudukkona ja valita pisteitä kahdeksan pisteen kuution muotoinen rypäs kerrallaan ja tutkia kulmien tiheyksiä. Kun vaihtoehdot ovat että kukin kulma on joko kiinteää ainetta tai tyhjää, on tällaisella kuutiolla `2^8 = 256` mahdollista *konfiguraatiota*.

Konfiguraatiota vastaavat polygonit voidaan täten suoraan rakentaa hakutaulun tavoin toimivan konfiguraatiotaulun avulla. Itse isosurfacen konstruointi ei siis näiltäosin ole kovin haastavaa, vaan haasteet piilevät läpikäytävän osa-avaruuden valinnassa.


Algoritmien aikavaativuus ja tietorakenteet
-------------------------------------------

### Marching Cubes
*Marching Cubes* on itsessään `O(n)` -aikainen, avaruudesta valittavan pistejoukon koon `n` suhteen. Joukon koko `n` puolestaan riippuu näytteenoton *resoluutiosta* `r` eli siitä montako näytteenottopistettä yhteen tilayksikköön mahtuu. Tässä huomattavaa on että kuution muotoisessa avaruudessa, jossa tahkon leveys on `x`, saadaan `n` laskettua kaavalla
```
n = (xr)^3
```
Eli algoritmin aikavaativuus on itseasiassa kuutiollinen näytteenottoresoluution ja avaruuden koon välisen suhteen mukaan!

Naiivi lähestymistapa olisi vain etsiä polun ääripäiden koordinaatit ja muodostaa näitä *(ja jotakin sopivaa marginaalia)* käyttäen kuution muotoinen koordinaattiavaruus polun ympärille. Näin meillä on säännöllisen muotoinen ala joka voidaan läpikäydä resoluution mukaisella askelkoolla. Tämä kuitenkin johtaa tilanteeseen jossa todennäköisesti käytetään 30-70% laskenta-ajasta "tyhjien" kuutioiden läpikäyntiin. Kuten yllä todettiin, koska avaruuden koko vaikuttaa kuutiollisesti näytteiden määrään `n`, ei tämä ole kestävä ratkaisu kovin suurilla avaruuksilla.

Isosurfacen generoinnin suurin haaste tulee siis todennäköisesti olemaan läpikäytävän osa-avaruuden pienentäminen mahdollisimman tarkaksi jotta resoluutiota ja avaruutta voidaan vastaavasti kasvattaa mahdollisimman suureksi ilman että `n` kasvaa .


### Random Walk
*Random walk* on aikavaativuudeltaan yksinkertainen `O(n)` askeleen generointi. Aikavaativuus on suoraan riippuvainen otettavien askelten lukumäärästä. Toisaalta myös data on yksinkertainen kokoelma pisteitä ja näiden välisiä kaaria, joten itsessään näiden varastoiminenkaan ei ole kovin haastavaa ja haarautumisen sallimiseksi voidaan toteuttaa binääripuuna.

Haastavampaa polun varastoinnista tulee kun otetaan huomioon että solmuja ja kaaria tulisi voida nopeasti etsiä jonkin avaruuden pisteen läheltä tiheysfunktiota varten. Tässä kohdin `O(n)` läpikäyntiä solmujen määrän `n` suhteen ei ole mahdollinen ratkaisu, vaan tarvitaan spatiaalinen tietorakenne puun indeksointiin. Polun muodostavan puun muuttumattomuuden vuoksi esimerkisi *octree* tai *k-D puu* ovat varteenotettavia vaihtoehtoja.

### Tiheysfunktio
Tiheysfunktio itsessään tulee olemaan mustaa magiaa, vektorimatematiikkaa ja näiden pohjalta painotettujen kohinafunktioiden summa.

Kohinafunktioina on tarkoitus käyttää *Simplex Noisea* ja mahdollisesti *Worley/Cell Noisea* 

#### Simplex Noise
Koska saumattomasti jatkuvan kohinan generoiminen kolmiuloitteisessa avaruudessa saattaa vaatia 4- tai 5-ulotteista kohinafuntiota, käytetään Ken Perlinin alkuperäiseen Perlin noisen sijaan myöhempää variaatiota, Simplex Noisea. Etuna on että aikavaativuus korkeammissa ulottuvuuksissa `N` on vain `O(N^2)`, alkuperäisen `O(2^N)` sijaan.

#### Worley Noise
Kivimäisten pintojen luomista varten voidaan käyttää esimerkiksi *Worley Noisea*, joka on yksinkertainen pisteiden välisiin etäisyyksiin pohjautuva algoritmi, jossa kunkin pisteen arvo voidaan laskea `n`:nnen lähimän pisteen etäisyytenä. Toisin sanoen, valitsemalla esimerkiksi `n = 2`:
 1. levitetään avaruuteen satunnaisesti pisteitä
 2. nyt minkä tahansa avaruuden pisteen arvo on sen etäisyys toiseksi lähimpään pisteeseen
 
Koska käytössä on säännöllinen ruudukko, ei tämä ole sellaisenaan kovin mielekästä etäisyyksien samankaltaisuuden vuoksi. Siksi *Worley Noise* voidaan laskea viimeisenä kun ruudukon näyteenottopisteillä on jo arvot ja käyttää olemassaolevia arvoja etäisyyksien vääristämiseen esimerkiksi siten että pisteiden välisenä etäisyydenä käytetään niiden tiheyden erotusta kerrottuna todellisella etäisyydellä.

#### Fractal Noise
Kun samaa kohinafunktiota kutsutaan eri kokoluokkiin skaalatuilla koordinaateilla useita kertoja ja lasketaan tuloksista painotettu summa tai keskiarvo, saadaan tulokseksi enemmän yksityiskohtia sisältävää nk. "fraktaalikohinaa".

Fractal noise ei sinällään siis ole kohinafunktio itsessään, vaan tapa tuottaa mielenkiintoisempia tuloksia muilla kohinafuntioilla.


### TL;DR:
#### Algot:
- Random Walk
- Marching Cubes
- Simplex Noise
- *(sovellettu)* Worley Noise
- Jokin pseudo-satunnaislukugeneraattori, esim. jokin *xorshift* variaatio

#### Tietorakenteet:
- Binääripuu
- Octree tai k-ulotteinen puu
- Kasvava lista *(vastaava kuin `ArrayList`)*

#### Tavoitteet
- Random Walk generoimaan puu josta voi hakea mielivaltaiselle pisteelle `n`:nnen lähimmän pisteen tehokkaasti. *( = Parempi kuin `O(N)`, jossa `N` on puun solmujen määrä)*
- Simplex noise joka toimii ajassa `O(D^2)`, jossa `D` on ulottuvuus
- Marching Cubes joka iteroidaan alle `O(W * H * D)` ajassa, jossa `W`, `H` ja `D` ovat avaruuden *leveys*, *korkeus* ja *syvyys*. *(Eli koko avaruutta ei turhaan iteroida läpi. Tunnetut tyhjät alueet jätetään iteroimatta)* 

#### Lähteet:

- [A survey of the marching cubes algorithm, Timothy S. Newman, Hong Yi, 2006](https://cg.informatik.uni-freiburg.de/intern/seminar/surfaceReconstruction_survey%20of%20marching%20cubes.pdf)
- [An Implementation of the Marching Cubes algorithm, Ben Andersson](http://www.cs.carleton.edu/cs_comps/0405/shape/marching_cubes.html)
- [Polygonising a scalar field, Paul Bourke, 1994](http://paulbourke.net/geometry/polygonise/)
- [Marching Cubes: A High Resolution 3D Surface Construction Algorithm, William E. Lorensen, Harvey. E. Cline, 1987](http://academy.cba.mit.edu/classes/scanning_printing/MarchingCubes.pdf)
- [Kisakoodarin Käsikirja, Antti Laaksonen, 2018](https://www.cs.helsinki.fi/u/ahslaaks/kkkk.pdf)
- [Cell Noise and Processing, Carl-Johan Rosén, 2006](http://www.carljohanrosen.com/share/CellNoiseAndProcessing.pdf)
- [Simplex Noise Demystified, Stefan Gustavsonm 2005](http://staffwww.itn.liu.se/~stegu/simplexnoise/simplexnoise.pdf)
- ["kd-Trees", University of Maryland, lecture slides](https://www.cs.cmu.edu/~ckingsf/bioinfo-lectures/kdtrees.pdf)
- [An intoductory tutorial on kd-trees
   Andrew W. Moore, 1991](https://www.ri.cmu.edu/pub_files/pub1/moore_andrew_1991_1/moore_andrew_1991_1.pdf)
- [Introduction to Octrees, Eric Nevala, 2014](https://www.gamedev.net/articles/programming/general-and-gameplay-programming/introduction-to-octrees-r3529/)
