Viikko 5.
---------
*Työmäärä tällä viikolla ~`n + 1` tuntia*

Spatiaaliset tietorakenteet osoittautuivat odotetusti työläiksi, mutta olen sysännyt niitä syrjään jo aivan liian pitkään joten ne oli nyt viimein saatava tehdyksi. Jo alustavat tulokset, ensimmäisellä, hieman hölmöllä Octree-toteutuksella viilasivat arvoilla `pathLength = 3200, SBS=4.0` lähes kymmenen sekuntia pois generointiin kuluvasta ajasta. Kun `pathLength = 16000`, uusi koodi tuotti luolan hieman yli kahdessa minuutissa, kun taas vanhalla generoidessa kyllästyin odottamaan kymmenen minuutin jälkeen. Oletus `n` vaikutuksesta generointiaikaan suuremmilla luolan pituuksista vaikuttaisi siis täsmäävän oletuksien kanssa.

Päätin myös aloittaa profiloinnin tässä vaiheessa jotta pääsisin paremmin perille mitä osia minun olisi järkevää lähteä viilaamaan tehokkaammiksi. Octree-implementaation toteutus rekursiolla osoittautui välittömästi huonoksi ajatukseksi, sillä syntyvistä pitkistä call-chaineista kertyy valtavasti hukkaan kuluvaa aikaa. Suurin kipupiste vaikutti kuitenkin olevan edelleen että tiheysfunktiota jouduttiin yksinkertaisesti evaluoimaan liikaa.

Tilanteen korjaamiseksi tein muutin syvyyshakua siten että myös kokonaan tyhjää olevat kuutiot hylätään automaattisesti. Tämä vaati myös aloituspisteen valintalogiikan muuttamista hieman, mutta se onnistui yllättävän helposti. Optimoinnin tuloksena suoritusaika oli pudonnut puoleen alkuperäisestä. Generointi asettamalla `pathLength = 16000` kesti enää vain minuutin, joka kuulostaa edelleen melko pahalta, mutta kun verrataan viikon takaiseen määrittelemättömän pitkään 10+ minuuttiin, on parannus jo melkoinen.
