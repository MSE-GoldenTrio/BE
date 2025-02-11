// google.js
import { initializeApp, } from 'https://www.gstatic.com/firebasejs/10.4.0/firebase-app.js';
import { getAuth, GoogleAuthProvider, signInWithPopup, } from 'https://www.gstatic.com/firebasejs/10.4.0/firebase-auth.js';

const projectIdInput = document.getElementById('projectId');
const apiKeyInput = document.getElementById('apiKey');
const authDomainInput = document.getElementById('authDomain');
const googleButton = document.getElementById('google');
const responseTextarea = document.getElementById('response');

googleButton.addEventListener('click', () => {
    const projectId = projectIdInput.value;
    const apiKey = apiKeyInput.value;
    const authDomain = authDomainInput.value;

    const firebaseApp = initializeApp({
        projectId: projectId,
        apiKey: apiKey,
        authDomain: authDomain,
    });

    const auth = getAuth(firebaseApp);

    const googleAuthProvider = new GoogleAuthProvider();
    googleAuthProvider.setDefaultLanguage('ko');
    googleAuthProvider.setCustomParameters({
        login_hint: 'user@example.com',
    });

    signInWithPopup(auth, googleAuthProvider)
        .then((userCredential) => {
            responseTextarea.value = JSON.stringify(userCredential, null, 2);

            userCredential.user.getIdToken()
                .then((idToken) => {
                    console.log(idToken);

                    const refreshToken = userCredential.user.refreshToken;
                    console.log(refreshToken);
                });
        });
});