# À propos de l'application Web conference
    
* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright CGI, Région Nouvelle Aquitaine
* Financeur(s) : CGI, Nouvelle Aquitaine
* Développeur(s) : CGI
* Description : L’application Web-conférence permet de créer et de partager des salles de web-conférences aux utilisateurs de la plateforme. La liste de vos salles créées est affichée et vous pouvez les gérer directement depuis cet écran.
       
## Configuration
<pre>
{
  "config": {
    ...
    "allow-public-link": ${allowPublicLink},
    ...
    "zimbra-max-recipients": "${zimbraMaxRecipients}",
    "bigbluebutton": {
        "host": "${webConferenceBBBHost}",
        "api_endpoint": "${webConferenceBBBAPIEndpoint}",
        "secret": "${webConferenceBBBAPIEndpoint}"
    }
  }
}
</pre>

Dans votre springboard, vous devez inclure des variables d'environnement :

<pre>
allowPublicLink = ${String}
zimbraMaxRecipients = Integer
webConferenceBBBHost = ${String}
webConferenceBBBAPIEndpoint = ${String}
</pre>