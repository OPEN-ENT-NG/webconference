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
    },
    "share": {
        "overrideDefaultActions": "${webConferenceDefaultShare}"
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
webConferenceDefaultShare = Array(String)
</pre>

Dans votre configuration nginx du springboard, vous devez spécifier la politique de referer
afin de pouvoir renvoyer correctement le path pour l'ouverture de salle.

<pre>
location /webconference {
proxy_pass http://vertx:8090;
add_header Referrer-Policy "origin-when-cross-origin";
}
</pre>

Par défault, cette valeur est initialisée à "strict-origin"