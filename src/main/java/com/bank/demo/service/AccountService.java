package com.bank.demo.service;

import com.bank.demo.auth.JWTUtils;
import com.bank.demo.data.Account;
import com.bank.demo.dto.CreateRequest;
import com.bank.demo.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AccountService {
    @Lazy
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StoreService storeService;

    @Lazy
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JWTUtils jwtUtils;

    public Map<String, String> getCleanAccount(String accountNo) throws IOException {
        String data = storeService.readData();
        if(data.isEmpty()){
            throw new ResponseStatusException(NOT_FOUND, "Accounts does not exist");
        }
        Map<String, Map<String, Object>> map = storeService.convertToMap(data);
        Map<String, Object> account = map.get("accounts");
        if(account.containsKey(accountNo)){
            Object acc = account.get(accountNo);
            ObjectMapper mapper = new ObjectMapper();
            Account account1 =  mapper.convertValue(acc, Account.class);
            return new LinkedHashMap<>(){{
                put("accountNumber", account1.getAccountNumber());
                put("accountName", account1.getAccountName());
                put("balance", account1.getBalance().toString());
            }};
        }
        throw new ResponseStatusException(NOT_FOUND, "Account not found");
    }

    public Account getAccount(String accountNo) throws IOException {
        String data = storeService.readData();
        if(data.isEmpty()){
            throw new ResponseStatusException(NOT_FOUND, "Account Files are not Registered");
        }
        Map<String, Map<String, Object>> map = storeService.convertToMap(data);
        Map<String, Object> account = map.get("accounts");
        if(account == null){
            throw new ResponseStatusException(NOT_FOUND, "Account not found");
        }
        if(account.containsKey(accountNo)){
            Object acc = account.get(accountNo);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.convertValue(acc, Account.class);
        }
        throw new ResponseStatusException(NOT_FOUND, "Account not found");
    }
    public String createAccount(CreateRequest request) throws IOException {
        request.setAccountPassword(passwordEncoder.encode(request.getAccountPassword()));
        String accountNo = generateRandomNumbers(10);
        Account account = new Account(request.getAccountName(), accountNo, request.getInitialDeposit(), request.getAccountPassword());
        try{
            String data = storeService.readData();
            if(data.isEmpty()){
                return instantiateDB(account);
            }
            Map<String, Map<String, Object>> map = storeService.convertToMap(data);
            Map<String, Object> accountMap = map.get("accounts");
            if(accountMap == null){
                return instantiateDB(account);
            }
            ObjectMapper mapper = new ObjectMapper();
            for (Object accObj : accountMap.values())
                if((mapper.convertValue(accObj, Account.class).getAccountName()).equalsIgnoreCase(request.getAccountName()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Account with name "+ request.getAccountName() +" already exists");
            map.get("accounts").put(account.getAccountNumber(), account);
            storeService.writeData(map);
            return account.getAccountNumber();
        }catch (ResponseStatusException e){
            throw e;
        }
    }

    public String generateRandomNumbers(int max){
        StringBuilder string = new StringBuilder(max);
        for(int i = 0; i<max; i++){
            Random randNum = new Random();
            int x = randNum.nextInt(9);
            string.append(x);
        }
        return string.toString();
    }
    public String loginAccount(LoginRequest request) throws IOException {
        try{
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getAccountNumber(),request.getAccountPassword()));
        }catch (BadCredentialsException ex){
            throw new ResponseStatusException(UNAUTHORIZED,ex.getMessage());
        }
        Account account = getAccount(request.getAccountNumber());
        Map<String, String> claims = new HashMap<>(){{
            put("accountName",account.getAccountName());
            put("enabled","true");
        }};
        String token = jwtUtils.createToken(account.getAccountNumber(), claims);
        return token;
    }
    private String instantiateDB(Account account) throws IOException {
        Map<String, Map<String, Object>> db = new HashMap<>();
        db.put("accounts", new HashMap<>(){{
            put(account.getAccountNumber(), account);
        }});
        storeService.writeData(db);
        return account.getAccountNumber();
    }
}
