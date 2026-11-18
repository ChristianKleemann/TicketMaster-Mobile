package com.example.ticketmaster_mobile.ui.tickets;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.ticketmaster_mobile.PaymentValidationUtils;
import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.FechaUtils;
import com.example.ticketmaster_mobile.data.entity.MetodoPago;
import com.example.ticketmaster_mobile.data.repository.CompraRepository;
import com.example.ticketmaster_mobile.session.SessionManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pago: muestra el monto a pagar y la cantidad de boletos, y solicita TODOS los datos del método
 * elegido. Tarjeta (crédito/débito): número (solo dígitos), nombre (solo texto), vencimiento y CVV.
 * PayPal: correo de la cuenta y nombre del titular.
 */
public class PaymentActivity extends AppCompatActivity {

    public static final String EXTRA_ID_FUNCION = "EXTRA_ID_FUNCION";
    public static final String EXTRA_NOMBRE_EVENTO = "EXTRA_NOMBRE_EVENTO";
    public static final String EXTRA_FECHA_HORA = "EXTRA_FECHA_HORA";
    /** Un elemento por boleto. -1 en IDS_ASIENTOS significa "sin asiento numerado". */
    public static final String EXTRA_IDS_ASIENTOS = "EXTRA_IDS_ASIENTOS";
    public static final String EXTRA_IDS_ZONAS = "EXTRA_IDS_ZONAS";
    public static final String EXTRA_PRECIOS = "EXTRA_PRECIOS";
    public static final String EXTRA_ETIQUETAS = "EXTRA_ETIQUETAS";
    public static final String EXTRA_TOTAL = "EXTRA_TOTAL";

    private RadioGroup rgMetodosPago;
    private View llFormTarjeta, llFormPaypal;
    private TextInputLayout tilNumero, tilNombreTarjeta, tilVencimiento, tilCvv, tilCorreoPaypal, tilNombrePaypal;
    private TextInputEditText etNumero, etNombreTarjeta, etVencimiento, etCvv, etCorreoPaypal, etNombrePaypal;
    private Button btnConfirmarPago;

    private int idFuncion;
    private int[] idsAsientos;
    private int[] idsZonas;
    private double[] precios;

    private final Map<Integer, String> nombrePorMetodo = new HashMap<>();

    private AppDatabase db;
    private CompraRepository compraRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        Toolbar toolbar = findViewById(R.id.toolbarPago);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        db = AppDatabase.getInstance(this);
        compraRepository = new CompraRepository(this);
        sessionManager = new SessionManager(this);

        Intent in = getIntent();
        idFuncion = in.getIntExtra(EXTRA_ID_FUNCION, -1);
        idsAsientos = in.getIntArrayExtra(EXTRA_IDS_ASIENTOS);
        idsZonas = in.getIntArrayExtra(EXTRA_IDS_ZONAS);
        precios = in.getDoubleArrayExtra(EXTRA_PRECIOS);
        String[] etiquetas = in.getStringArrayExtra(EXTRA_ETIQUETAS);
        double total = in.getDoubleExtra(EXTRA_TOTAL, 0.0);

        if (idsZonas == null || precios == null || idsAsientos == null) {
            Toast.makeText(this, "No se recibieron boletos para pagar", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.tvResumenEvento)).setText(in.getStringExtra(EXTRA_NOMBRE_EVENTO));
        ((TextView) findViewById(R.id.tvResumenFecha)).setText(FechaUtils.fechaHoraLegible(in.getStringExtra(EXTRA_FECHA_HORA)));
        ((TextView) findViewById(R.id.tvResumenAsientos)).setText(resumirEtiquetas(etiquetas));
        ((TextView) findViewById(R.id.tvResumenCantidad)).setText("Cantidad de boletos: " + precios.length);
        ((TextView) findViewById(R.id.tvResumenTotal)).setText(String.format(Locale.getDefault(), "Monto a pagar: $%,.2f", total));

        rgMetodosPago = findViewById(R.id.rgMetodosPago);
        llFormTarjeta = findViewById(R.id.llFormTarjeta);
        llFormPaypal = findViewById(R.id.llFormPaypal);
        tilNumero = findViewById(R.id.tilNumeroTarjeta);
        tilNombreTarjeta = findViewById(R.id.tilNombreTarjeta);
        tilVencimiento = findViewById(R.id.tilVencimiento);
        tilCvv = findViewById(R.id.tilCvv);
        tilCorreoPaypal = findViewById(R.id.tilCorreoPaypal);
        tilNombrePaypal = findViewById(R.id.tilNombrePaypal);
        etNumero = findViewById(R.id.etNumeroTarjeta);
        etNombreTarjeta = findViewById(R.id.etNombreTarjeta);
        etVencimiento = findViewById(R.id.etVencimiento);
        etCvv = findViewById(R.id.etCvv);
        etCorreoPaypal = findViewById(R.id.etCorreoPaypal);
        etNombrePaypal = findViewById(R.id.etNombrePaypal);
        btnConfirmarPago = findViewById(R.id.btnConfirmarPago);

        configurarCampos();
        cargarMetodosPago();
        btnConfirmarPago.setOnClickListener(v -> confirmarPago());
    }

    /** Agrupa "Palco, Palco, Palco" como "Palco ×3" para mostrarlo compacto. */
    private String resumirEtiquetas(String[] etiquetas) {
        if (etiquetas == null) return "";
        Map<String, Integer> conteo = new java.util.LinkedHashMap<>();
        for (String e : etiquetas) {
            Integer c = conteo.get(e);
            conteo.put(e, c == null ? 1 : c + 1);
        }
        StringBuilder sb = new StringBuilder("Boletos: ");
        boolean primero = true;
        for (Map.Entry<String, Integer> e : conteo.entrySet()) {
            if (!primero) sb.append(", ");
            primero = false;
            sb.append(e.getKey());
            if (e.getValue() > 1) sb.append(" ×").append(e.getValue());
        }
        return sb.toString();
    }

    private void configurarCampos() {
        // El nombre del titular solo admite letras (con acentos y ñ) y espacios
        InputFilter soloTexto = (source, start, end, dest, dstart, dend) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (Character.isLetter(c) || c == ' ') sb.append(c);
            }
            return sb.length() == end - start ? null : sb.toString();
        };
        InputFilter[] filtroNombre = new InputFilter[]{soloTexto, new InputFilter.LengthFilter(50)};
        etNombreTarjeta.setFilters(filtroNombre);
        etNombrePaypal.setFilters(filtroNombre);

        // Inserta la "/" automáticamente en MM/AA
        etVencimiento.addTextChangedListener(new TextWatcher() {
            private boolean editando = false;

            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (editando) return;
                editando = true;
                String digitos = s.toString().replace("/", "");
                if (digitos.length() > 4) digitos = digitos.substring(0, 4);
                String formateado = digitos.length() > 2 ? digitos.substring(0, 2) + "/" + digitos.substring(2) : digitos;
                if (!formateado.equals(s.toString())) {
                    s.replace(0, s.length(), formateado);
                }
                editando = false;
            }
        });
    }

    private void cargarMetodosPago() {
        db.metodoPagoDao().observarTodos().observe(this, metodos -> {
            rgMetodosPago.setOnCheckedChangeListener(null);
            rgMetodosPago.removeAllViews();
            nombrePorMetodo.clear();
            if (metodos == null) return;
            for (int i = 0; i < metodos.size(); i++) {
                MetodoPago metodo = metodos.get(i);
                nombrePorMetodo.put(metodo.idMetodoPago, metodo.nombre);
                RadioButton rb = new RadioButton(this);
                rb.setId(metodo.idMetodoPago);
                rb.setText(metodo.nombre);
                rb.setTextColor(getColor(R.color.tm_text_primary));
                rb.setPadding(0, 16, 0, 16);
                rgMetodosPago.addView(rb);
            }
            rgMetodosPago.setOnCheckedChangeListener((g, id) -> actualizarFormulario());
            if (!metodos.isEmpty()) rgMetodosPago.check(metodos.get(0).idMetodoPago);
            actualizarFormulario();
        });
    }

    private boolean esPaypalSeleccionado() {
        String nombre = nombrePorMetodo.get(rgMetodosPago.getCheckedRadioButtonId());
        return nombre != null && nombre.toLowerCase(Locale.ROOT).contains("paypal");
    }

    private void actualizarFormulario() {
        boolean paypal = esPaypalSeleccionado();
        llFormPaypal.setVisibility(paypal ? View.VISIBLE : View.GONE);
        llFormTarjeta.setVisibility(paypal ? View.GONE : View.VISIBLE);
    }

    /** Valida el formulario del método elegido. Devuelve los datos de pago o null si hay errores. */
    private CompraRepository.DatosPago validarYObtenerDatos(int idMetodo) {
        if (esPaypalSeleccionado()) {
            String correo = textoDe(etCorreoPaypal);
            String nombre = textoDe(etNombrePaypal);
            String errCorreo = PaymentValidationUtils.errorCorreo(correo);
            String errNombre = PaymentValidationUtils.errorNombre(nombre);
            tilCorreoPaypal.setError(errCorreo);
            tilNombrePaypal.setError(errNombre);
            if (errCorreo != null || errNombre != null) return null;
            return new CompraRepository.DatosPago(idMetodo, true, nombre.trim(), null, correo.trim());
        }

        String numero = textoDe(etNumero);
        String nombre = textoDe(etNombreTarjeta);
        String venc = textoDe(etVencimiento);
        String cvv = textoDe(etCvv);
        String errNumero = PaymentValidationUtils.errorNumeroTarjeta(numero);
        String errNombre = PaymentValidationUtils.errorNombre(nombre);
        String errVenc = PaymentValidationUtils.errorVencimiento(venc, Calendar.getInstance());
        String errCvv = PaymentValidationUtils.errorCvv(cvv);
        tilNumero.setError(errNumero);
        tilNombreTarjeta.setError(errNombre);
        tilVencimiento.setError(errVenc);
        tilCvv.setError(errCvv);
        if (errNumero != null || errNombre != null || errVenc != null || errCvv != null) return null;
        return new CompraRepository.DatosPago(idMetodo, false, nombre.trim(),
                numero.substring(numero.length() - 4), null);
    }

    private String textoDe(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString() : "";
    }

    private void confirmarPago() {
        int idMetodoSeleccionado = rgMetodosPago.getCheckedRadioButtonId();
        if (idMetodoSeleccionado == -1) {
            Toast.makeText(this, "Selecciona un método de pago", Toast.LENGTH_SHORT).show();
            return;
        }

        int idUsuario = sessionManager.getIdUsuarioActivo();
        if (idUsuario == -1) {
            Toast.makeText(this, "Tu sesión expiró. Vuelve a iniciar sesión.", Toast.LENGTH_SHORT).show();
            return;
        }

        CompraRepository.DatosPago datos = validarYObtenerDatos(idMetodoSeleccionado);
        if (datos == null) return;

        List<CompraRepository.ItemCompra> items = new ArrayList<>();
        for (int i = 0; i < precios.length; i++) {
            Integer idAsiento = idsAsientos[i] > 0 ? idsAsientos[i] : null;
            items.add(new CompraRepository.ItemCompra(idAsiento, idsZonas[i], precios[i]));
        }

        btnConfirmarPago.setEnabled(false);

        compraRepository.realizarCompra(idUsuario, idFuncion, items, datos, new CompraRepository.CompraCallback() {
            @Override
            public void onExito(int idOrden) {
                Intent intent = new Intent(PaymentActivity.this, ConfirmacionCompraActivity.class);
                intent.putExtra(ConfirmacionCompraActivity.EXTRA_ID_ORDEN, idOrden);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String mensaje) {
                btnConfirmarPago.setEnabled(true);
                new AlertDialog.Builder(PaymentActivity.this)
                        .setTitle("No se pudo completar el pago")
                        .setMessage(mensaje)
                        .setPositiveButton("Entendido", null)
                        .show();
            }
        });
    }
}
