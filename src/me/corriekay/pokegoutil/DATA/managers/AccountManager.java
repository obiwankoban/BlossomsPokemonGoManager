package me.corriekay.pokegoutil.DATA.managers;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.player.PlayerProfile;
import com.pokegoapi.auth.CredentialProvider;
import com.pokegoapi.auth.GoogleUserCredentialProvider;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import javafx.scene.control.Alert;
import me.corriekay.pokegoutil.DATA.enums.LoginType;
import me.corriekay.pokegoutil.DATA.models.LoginData;
import me.corriekay.pokegoutil.utils.ConfigKey;
import me.corriekay.pokegoutil.utils.ConfigNew;
import okhttp3.OkHttpClient;

/*this controller does the login/log off, and different account information (aka player data)
 *
 */
public final class AccountManager {

    private static AccountManager S_INSTANCE;
    private ConfigNew config = ConfigNew.getConfig();

    private PokemonGo go = null;
    private AccountManager() {

    }

    public static AccountManager getInstance() {
        if (S_INSTANCE == null) {
            S_INSTANCE = new AccountManager();
            //DO any required initialization stuff here
        }
        return S_INSTANCE;
    }

    public boolean login(LoginData loginData) throws Exception {
              
        switch (loginData.getLoginType()) {
            case GOOGLE:
                return logOnGoogleAuth(loginData);
            case PTC:
                return logOnPTC(loginData);
            default:
                return false;
        }
    }

    private boolean logOnPTC(LoginData loginData) throws Exception {
        OkHttpClient http;
        CredentialProvider cp;
        PokemonGo go;
        http = new OkHttpClient();
        
        String username = loginData.getUsername();
        String password = loginData.getPassword();
        boolean saveAuth = config.getBool(ConfigKey.LOGIN_SAVE_AUTH);

        try {
            cp = new PtcCredentialProvider(http, username, password);
            config.setString(ConfigKey.LOGIN_PTC_USERNAME, username);
            if (saveAuth) {
                config.setString(ConfigKey.LOGIN_PTC_PASSWORD, password);
            } else {
                deleteLoginData(LoginType.PTC);
            }
        } catch (Exception e) {
            alertFailedLogin(e.getMessage());
            deleteLoginData(LoginType.PTC);
            return false;
        }

        try {
            go = new PokemonGo(cp, http);
            S_INSTANCE.go = go;
            initOtherControllers(go);
            
            return true;
        } catch (LoginFailedException | RemoteServerException e) {
            alertFailedLogin(e.getMessage());
            deleteLoginData(LoginType.BOTH);
            return false;
        }

    }

    private boolean logOnGoogleAuth(LoginData loginData) {
        OkHttpClient http;
        CredentialProvider cp;
        PokemonGo go;
        http = new OkHttpClient();
        
        String authCode = loginData.getToken();
        boolean saveAuth = config.getBool(ConfigKey.LOGIN_SAVE_AUTH);

        boolean refresh = false;
        if (loginData.isSavedToken() && saveAuth) {
            refresh = true;
        }

        try {
            GoogleUserCredentialProvider provider = new GoogleUserCredentialProvider(http);
            if (refresh) {
                provider.refreshToken(authCode);
            } else {
                provider.login(authCode);
            }

            if (provider.isTokenIdExpired()) {
                throw new LoginFailedException();
            }

            cp = provider;
            if (saveAuth && !refresh) {
                config.setString(ConfigKey.LOGIN_GOOGLE_AUTH_TOKEN, provider.getRefreshToken());
            } else {
                deleteLoginData(LoginType.GOOGLE);
            }
        } catch (Exception e) {
            alertFailedLogin(e.getMessage());
            deleteLoginData(LoginType.GOOGLE);
            return false;
        }

        try {
            go = new PokemonGo(cp, http);
            S_INSTANCE.go = go;
            initOtherControllers(go);
            
            return true;
        } catch (LoginFailedException | RemoteServerException e) {
            alertFailedLogin(e.getMessage());
            deleteLoginData(LoginType.BOTH);
            return false;
        }
        
    }

    private void initOtherControllers(PokemonGo go) {
        InventoryManager.initialize(go);
        PokemonBagManager.initialize(go);
        ProfileManager.initialize(go);
    }

    public void alertFailedLogin(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error Login");
        alert.setHeaderText("Unfortunately, your login has failed");
        alert.setContentText(message != null ? message : "" + "\nPress OK to try again.");
        alert.showAndWait();
    }

    public LoginData getLoginData(LoginType type) {
        LoginData loginData = new LoginData();
        
        switch (type) {
            case GOOGLE:
                 loginData.setToken(config.getString(ConfigKey.LOGIN_GOOGLE_AUTH_TOKEN));
                 loginData.setSavedToken(true);
                 break;
            case PTC:
                loginData.setUsername(config.getString(ConfigKey.LOGIN_PTC_USERNAME));
                loginData.setPassword(config.getString(ConfigKey.LOGIN_PTC_PASSWORD));
                break;
            case BOTH:
                loginData.setToken(config.getString(ConfigKey.LOGIN_GOOGLE_AUTH_TOKEN));
                loginData.setSavedToken(true);
                loginData.setUsername(config.getString(ConfigKey.LOGIN_PTC_USERNAME));
                loginData.setPassword(config.getString(ConfigKey.LOGIN_PTC_PASSWORD));
                break;
            default:
        }
        return loginData;        
        
    }

    private void deleteLoginData(LoginType type) {
        deleteLoginData(type, false);
    }

    private void deleteLoginData(LoginType type, boolean justCleanup) {
        if (!justCleanup) config.delete(ConfigKey.LOGIN_SAVE_AUTH);
        switch (type) {
            case BOTH:
                config.delete(ConfigKey.LOGIN_GOOGLE_AUTH_TOKEN);
                config.delete(ConfigKey.LOGIN_PTC_USERNAME);
                config.delete(ConfigKey.LOGIN_PTC_PASSWORD);
                break;
            case GOOGLE:
                config.delete(ConfigKey.LOGIN_GOOGLE_AUTH_TOKEN);
                break;
            case PTC:
                config.delete(ConfigKey.LOGIN_PTC_USERNAME);
                config.delete(ConfigKey.LOGIN_PTC_PASSWORD);
                break;
            default:
        }
    }

    public boolean checkForSavedCredentials() {
        LoginType savedLogin = checkSavedConfig();
        // TODO: Implement choose if you want to login with that saved data
        if (savedLogin == LoginType.NONE) return false;
        else return true;
    }

    public LoginType checkSavedConfig() {
        if (!config.getBool(ConfigKey.LOGIN_SAVE_AUTH)) {
            return LoginType.NONE;
        } else {
            boolean google = getLoginData(LoginType.GOOGLE) != null;
            boolean PTC = getLoginData(LoginType.PTC) != null;
            if (google && PTC) return LoginType.BOTH;
            if (google) return LoginType.GOOGLE;
            if (PTC) return LoginType.PTC;
            return LoginType.NONE;
        }
    }

    public PlayerProfile getPlayerProfile() {
        return S_INSTANCE.go != null ? S_INSTANCE.go.getPlayerProfile() : null;
    }

    public void setSaveLogin(boolean save){
        config.setBool(ConfigKey.LOGIN_SAVE_AUTH, save);
    }
}
