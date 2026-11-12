        Administrator admin = administratorRepository.findByEmailIgnoreCase(STORE_ADMIN_EMAIL)
                .or(() -> administratorRepository.findByStore_Id(store.getId()))
                .orElseGet(Administrator::new);

        admin.setFirstName("María");
        admin.setLastName("Dayu");
        admin.setEmail(STORE_ADMIN_EMAIL);
        admin.setPassword(passwordEncoder.encode(demoPassword));
        admin.setRole(AdministratorRole.STORE_ADMIN);
        admin.setStore(store);
        admin.setActive(true);
        administratorRepository.save(admin);
    }

    private ClientAccount createClientAndAccount(Store store){
        Client client = clientRepository.findByEmailIgnoreCase(CLIENT_EMAIL)
                .or(() -> clientRepository.findByDocumentNumber(CLIENT_DOCUMENT))
                .orElseGet(Client::new);

        client.setFirstName("Ana");
        client.setLastName("Torres");
        client.setDocumentNumber(CLIENT_DOCUMENT);
        client.setEmail(CLIENT_EMAIL);
        client.setPassword(passwordEncoder.encode(demoPassword));
        client.setPhone("999111222");
        client.setActive(true);
        client = clientRepository.save(client);

        ClientAccount account = clientAccountRepository.findByClient_IdAndStore_Id(client.getId(), store.getId())
                .orElseGet(ClientAccount::new);
        account.setClient(client);
        account.setStore(store);
        account.setTeaPactada(new BigDecimal("0.20"));
        account.setTeaMoratoriaPactada(new BigDecimal("0.12"));
        account.setCurrency(CreditCurrency.PEN);
        account.setLimiteCredito(new BigDecimal("1000.00"));
        account.setPlazoMaximoMeses(4);
        account.setDiaCorte(15);
        account.setActive(true);
        return clientAccountRepository.save(account);
    }

    private List<Product> createProducts(Store store){
        return List.of(
                getOrCreateProduct(store, "Piñata unicornio", null, "Piñata grande de unicornio para cumpleaños y celebraciones", "85.00", "95.00"),
                getOrCreateProduct(store, "Pack de 200 globos", null, "Pack surtido de 200 globos para decoración de fiestas", "72.00", "82.00"),
                getOrCreateProduct(store, "Pack de 12 globos temáticos", "Pack de globos", "Doce globos temáticos para complementar la decoración", "48.00", "56.00"),
                getOrCreateProduct(store, "Pack de 12 peluches", "Pack de peluches", "Doce peluches pequeños para regalos, premios o decoración", "118.00", "132.00"),
                getOrCreateProduct(store, "Pack carpa cumpleañera", null, "Set decorativo tipo carpa para mesa principal de cumpleaños", "145.00", "160.00"),
                getOrCreateProduct(store, "Pack premios para piñata", "Pack de premios", "Surtido de premios pequeños para rellenar la piñata", "55.00", "65.00"),
                getOrCreateProduct(store, "Pack utensilios para comer", "Pack de utensilios", "Vasos, platos y utensilios desechables para una celebración", "60.00", "70.00"),
                getOrCreateProduct(store, "Estante para postres", null, "Estante decorativo para organizar dulces y postres de la mesa", "135.00", "150.00")
        );
    }

    private Product getOrCreateProduct(Store store, String name, String previousName, String description, String cashPrice, String creditPrice){
        Product product = productRepository.findByStore_IdAndName(store.getId(), name)
                .orElseGet(() -> previousName == null
                        ? createProduct(store, name)
                        : productRepository.findByStore_IdAndName(store.getId(), previousName)
                        .orElseGet(() -> createProduct(store, name)));

        product.setName(name);
        product.setSupplier("Dayu");
        product.setBrand("Dayu");
        product.setDescription(description);
        product.setUnitOfMeasure("unidad");
        product.setCashPrice(new BigDecimal(cashPrice));
        product.setCreditPrice(new BigDecimal(creditPrice));
        product.setAllowsInstallments(true);
        product.setActive(true);
        return productRepository.save(product);
    }

    private Product createProduct(Store store, String name){
        Product product = new Product();
        product.setStore(store);
        product.setName(name);
        return product;
    }

    private void createFrenchPlan(ClientAccount account, Product product){
        boolean activePlanExists = creditPlanRepository
                .findFirstByPurchase_ClientAccount_Client_IdAndStatus(account.getClient().getId(), CreditPlanStatus.ACTIVE)
                .isPresent();

        if(activePlanExists) return;

        BigDecimal ventaPrecio = new BigDecimal("500.00");

        Purchase purchase = new Purchase();
        purchase.setClientAccount(account);
        purchase.setPurchaseDate(LocalDate.now().minusDays(40));
        purchase.setPaymentMode(PurchasePaymentMode.INSTALLMENTS);
        purchase.setStatus(PurchaseStatus.FINANCED);
        purchase.setTotal(ventaPrecio);

        PurchaseDetail detail = new PurchaseDetail();
        detail.setPurchase(purchase);
        detail.setProduct(product);
        detail.setQuantity(1);
        detail.setUnitPrice(ventaPrecio);
        detail.setSubtotal(ventaPrecio);
        purchase.getDetails().add(detail);

        purchase = purchaseRepository.save(purchase);
        creditPlanService.createPlan(purchase, ventaPrecio, BigDecimal.ZERO, 4, true);
    }
}
