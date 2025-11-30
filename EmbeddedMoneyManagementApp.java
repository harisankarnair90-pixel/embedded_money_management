package dsaproject;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class EmbeddedMoneyManagementApp {
//Data Classes
    static class Expense {
        int id;
        String category;
        String storeName;
        String storeID;
        double amount;
        String date;

        Expense(int id, String category, String storeName, String storeID, double amount, String date) {
            this.id = id;
            this.category = category;
            this.storeName = storeName;
            this.storeID = storeID;
            this.amount = amount;
            this.date = date;
        }
    }

    static class Store {
        private final List<Expense> expenses = new ArrayList<>();
        private int nextId = 1;

        public synchronized void addExpense(String category, String storeName, String storeID, double amount, String date) {
            expenses.add(new Expense(nextId++, category, storeName, storeID, amount, date));
        }

        public synchronized List<Expense> getExpenses() {
            return new ArrayList<>(expenses);
        }

        public synchronized void deleteExpense(int id) {
            expenses.removeIf(e -> e.id == id);
        }

        public synchronized Map<String, Double> getCategoryTotals() {
            Map<String, Double> totals = new LinkedHashMap<>();
            for (Expense e : expenses)
                totals.put(e.category, totals.getOrDefault(e.category, 0.0) + e.amount);
            return totals;
        }

        public synchronized double getTotalSpending() {
            return expenses.stream().mapToDouble(e -> e.amount).sum();
        }
    }

    private static final Store store = new Store();
//Main
    public static void main(String[] args) throws Exception {
        int port = 8081;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new LoginHandler());
        server.createContext("/home", new HomeHandler());
        server.createContext("/api/expenses", new ExpensesHandler());
        server.createContext("/api/add", new AddExpenseHandler());
        server.createContext("/api/delete", new DeleteExpenseHandler());
        server.createContext("/api/analysis", new AnalysisHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        System.out.println("Server started at http://localhost:" + port);
        server.start();
    }
//Login Page
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> data = parseQuery(body);
                String username = data.get("username");
                String password = data.get("password");

                if ("user1".equals(username) && "demo1".equals(password)) {
                    exchange.getResponseHeaders().add("Location", "/home");
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    sendResponse(exchange, loginPage("Invalid credentials. Try again."));
                }
            } else {
                sendResponse(exchange, loginPage(null));
            }
        }

        private String loginPage(String errorMsg) {
            return """
                <html>
                <head>
                    <title>Login</title>
                    <style>
                        body {font-family: Arial; background: #f3f4f6; display:flex; justify-content:center; align-items:center; height:100vh;}
                        .login-box {background:#fff; padding:30px; border-radius:12px; box-shadow:0 2px 8px rgba(0,0,0,0.1); width:300px;}
                        input {width:100%; padding:10px; margin:10px 0; border:1px solid #ccc; border-radius:8px;}
                        button {width:100%; background:#2563eb; color:white; padding:10px; border:none; border-radius:8px; cursor:pointer;}
                        button:hover {background:#1e40af;}
                        .error {color:red; text-align:center;}
                    </style>
                </head>
                <body>
                    <div class='login-box'>
                        <h2 style='text-align:center;'>Money Management Login</h2>
                        """ + (errorMsg != null ? "<p class='error'>" + errorMsg + "</p>" : "") + """
                        <form method='POST'>
                            <input type='text' name='username' placeholder='Username' required>
                            <input type='password' name='password' placeholder='Password' required>
                            <button type='submit'>Login</button>
                        </form>
                    </div>
                </body>
                </html>
            """;
        }
    }
//Home Page
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendResponse(exchange, homePage());
        }

        private String homePage() {
            return """
                <html>
                <head>
                    <title>Money Management</title>
                    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
                    <style>
                        body {font-family:Arial;background:#f3f4f6;margin:0;padding:0;}
                        header {background:#2563eb;color:white;text-align:center;padding:15px;}
                        main {padding:20px;max-width:900px;margin:auto;}
                        input, select, button {padding:8px;margin:5px;border-radius:6px;border:1px solid #ccc;}
                        button {background:#2563eb;color:white;border:none;cursor:pointer;}
                        button:hover {background:#1e40af;}
                        table {width:100%;border-collapse:collapse;margin-top:20px;}
                        th, td {border:1px solid #ddd;padding:10px;text-align:center;}
                        th {background:#2563eb;color:white;}
                        section {margin-top:40px;}
                        canvas {max-width:100%;height:400px;}
                        #noDataMsg {text-align:center;color:#888;}
                        #totalSpending {font-weight:bold; color:#2563eb; text-align:center; margin-top:20px;}
                    </style>
                </head>
                <body>
                    <header><h2>Money Management Dashboard</h2></header>
                    <main>
                        <h3>Add Expense</h3>
                        <form id='expenseForm'>
                            <select id='category'>
                                <option>Food</option>
                                <option>Travel</option>
                                <option>Bills</option>
                                <option>Entertainment</option>
                                <option>Supplies</option>
                                <option>Clothing</option>
                            </select>
                            <input id='storeName' placeholder='Store Name' required>
                            <input id='storeID' placeholder='Store ID' required>
                            <input id='amount' type='number' placeholder='Amount' required>
                            <button type='submit'>Add</button>
                        </form>

                        <h3>Expenses</h3>
                        <table id='expenseTable'>
                            <thead><tr><th>ID</th><th>Category</th><th>Store</th><th>Store ID</th><th>Amount</th><th>Date</th><th>Action</th></tr></thead>
                            <tbody></tbody>
                        </table>

                        <section id='analysis'>
                            <h3>Spending Analysis</h3>
                            <div id='noDataMsg' style='display:none;'>No expenses to analyze yet.</div>
                            <canvas id='pieChart'></canvas>
                            <table id='categoryTotals'>
                                <thead><tr><th>Category</th><th>Total Amount</th></tr></thead>
                                <tbody></tbody>
                            </table>
                            <div id='totalSpending'></div>
                        </section>
                    </main>

                    <script>
                        let chartInstance = null;

                        async function loadExpenses() {
                            let res = await fetch('/api/expenses');
                            let data = await res.json();
                            let tbody = document.querySelector('#expenseTable tbody');
                            if (data.length === 0) {
                                tbody.innerHTML = "<tr><td colspan='7'>No expenses yet.</td></tr>";
                            } else {
                                tbody.innerHTML = data.map(e => 
                                    `<tr><td>${e.id}</td><td>${e.category}</td><td>${e.storeName}</td><td>${e.storeID}</td><td>${e.amount}</td><td>${e.date}</td><td><button onclick='del(${e.id})'>Delete</button></td></tr>`
                                ).join('');
                            }
                            loadAnalysis();
                        }

                        document.getElementById('expenseForm').onsubmit = async e => {
                            e.preventDefault();
                            let body = new URLSearchParams({
                                category: category.value,
                                storeName: storeName.value,
                                storeID: storeID.value,
                                amount: amount.value
                            });
                            await fetch('/api/add', {method:'POST', body});
                            e.target.reset();
                            loadExpenses();
                        };

                        async function del(id) {
                            await fetch('/api/delete?id=' + id, {method:'DELETE'});
                            loadExpenses();
                        }

                        async function loadAnalysis() {
                            let res = await fetch('/api/analysis');
                            let data = await res.json();
                            let ctx = document.getElementById('pieChart');
                            let msg = document.getElementById('noDataMsg');
                            let tableBody = document.querySelector('#categoryTotals tbody');
                            let totalText = document.getElementById('totalSpending');

                            if (data.length === 0) {
                                msg.style.display = 'block';
                                ctx.style.display = 'none';
                                tableBody.innerHTML = '';
                                totalText.innerHTML = '';
                                if (chartInstance) chartInstance.destroy();
                                return;
                            }

                            msg.style.display = 'none';
                            ctx.style.display = 'block';

                            let labels = data.map(d => d.category);
                            let totals = data.map(d => d.total);
                            let totalSpending = data.reduce((sum, d) => sum + d.total, 0);

                            if (chartInstance) chartInstance.destroy();

                            chartInstance = new Chart(ctx, {
                                type: 'pie',
                                data: {
                                    labels: labels,
                                    datasets: [{
                                        data: totals,
                                        backgroundColor: ['#60a5fa','#f87171','#34d399','#fbbf24','#a78bfa','#f472b6']
                                    }]
                                },
                                options: {responsive:true,plugins:{legend:{position:'bottom'}}}
                            });

                            tableBody.innerHTML = data.map(d => 
                                `<tr><td>${d.category}</td><td>${d.total.toFixed(2)}</td></tr>`
                            ).join('');

                            totalText.innerHTML = "Total Spending: " + totalSpending.toFixed(2);
                        }

                        loadExpenses();
                    </script>
                </body>
                </html>
            """;
        }
    }

//API HANDLERS
    static class ExpensesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = "[" + String.join(",", store.getExpenses().stream().map(e ->
                    String.format(Locale.US, "{\"id\":%d,\"category\":\"%s\",\"storeName\":\"%s\",\"storeID\":\"%s\",\"amount\":%.2f,\"date\":\"%s\"}",
                            e.id, e.category, e.storeName, e.storeID, e.amount, e.date)).toList()) + "]";
            sendJSON(exchange, json);
        }
    }

    static class AddExpenseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseQuery(body);
            store.addExpense(data.get("category"), data.get("storeName"), data.get("storeID"),
                    Double.parseDouble(data.get("amount")), new Date().toString());
            sendJSON(exchange, "{\"status\":\"ok\"}");
        }
    }

    static class DeleteExpenseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            store.deleteExpense(Integer.parseInt(params.get("id")));
            sendJSON(exchange, "{\"status\":\"deleted\"}");
        }
    }

    static class AnalysisHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Double> totals = store.getCategoryTotals();
            double grandTotal = store.getTotalSpending();
            String json = "[" + String.join(",", totals.entrySet().stream().map(e ->
                    String.format(Locale.US, "{\"category\":\"%s\",\"total\":%.2f,\"percentage\":%.2f}",
                            e.getKey(), e.getValue(), (e.getValue() / grandTotal) * 100)).toList()) + "]";
            sendJSON(exchange, json);
        }
    }
//Utilities
    private static void sendResponse(HttpExchange ex, String response) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/html");
        ex.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void sendJSON(HttpExchange ex, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2)
                map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return map;
    }
}
