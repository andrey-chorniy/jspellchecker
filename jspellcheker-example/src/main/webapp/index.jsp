<html>
<head>
    <script type="text/javascript" src="js/tinymce/tinymce.min.js"></script>
    <script type="text/javascript">
    tinymce.init({
        selector: "textarea.jazzy",
        plugins: "spellchecker",
        spellchecker_languages : "+English=en-us",
        spellchecker_rpc_url    : "http://localhost:8080/jspellchecker/jazzy-spellchecker"
     });

    tinymce.init({
        selector: "textarea.jmyspell",
        plugins: "spellchecker",
        spellchecker_languages : "+English=en-us",
        spellchecker_rpc_url    : "http://localhost:8080/jspellchecker/jmyspell-spellchecker"
     });

    tinymce.init({
        selector: "textarea.lucene",
        plugins: "spellchecker",
        spellchecker_languages : "+English=en-us",
        spellchecker_rpc_url    : "http://localhost:8080/jspellchecker/lucene-spellchecker"
     });

    tinymce.init({
        selector: "textarea.google_sp",
        plugins: "spellchecker",
        spellchecker_languages : "+English=en-us",
        spellchecker_rpc_url    : "http://localhost:8080/jspellchecker/google-spellchecker"
     });
    </script>
</head>
<body>
<h2>TinyMCE editor with spellchecker example</h2>
<form method="post">
    <table style="width:100%">
        <tr>
            <td>
                <h3>Jazzy Spellchecker</h3>
                <textarea class="jazzy"></textarea>
            </td>
            <td>
                <h3>JMySpellSpellchecker</h3>
                <textarea class="jmyspell"></textarea>
            </td>
        </tr>
        <tr>
            <td>
                <h3>Lucene Spellchecker</h3>
                <textarea class="lucene"></textarea>
            </td>
            <td>
                <h3>Google Spellchecker</h3>
                <textarea class="google_sp"></textarea>
            </td>
        </tr>
    </table>


</form>
</body>
</html>
