// scripts.js

// ========== PART 1: Utility & Upload Functions ==========

function setupAutoCompleteFilters() {
  const fields = {
    categoryFilter: "category",
    subcategoryFilter: "sub_category",
    supplierFilter: "supplier",
    statusFilter: "status"
  };

  Object.entries(fields).forEach(([inputId, key]) => {
    const $input = $(`#${inputId}`);

    $input.autocomplete({
      source: function (request, response) {
        const term = request.term || "";
        $.ajax({
          url: `https://my-spring-app-ck1f.onrender.com/purchase/autosuggest/${key}`,
          data: { q: term },
          success: function (data) {
            response(data || []);
          },
          error: function () {
            response([]);
          }
        });
      },
      minLength: 0
    }).focus(function () {
      // Trigger suggestions when input is empty
      $(this).autocomplete("search", "");
    });
  });// Ensure autocomplete width matches input
  $.ui.autocomplete.prototype._resizeMenu = function () {
    const ul = this.menu.element;
    ul.outerWidth(this.element.outerWidth());
  };

} function setupModalAutoCompleteFilters() {
  const fields = {
    modalCategory: "category",
    modalSupplier: "supplier"
  };

  Object.entries(fields).forEach(([inputId, key]) => {
    const $input = $(`#${inputId}`);

    // Destroy previous autocomplete to avoid duplicates
    if ($input.data("ui-autocomplete")) {
      $input.autocomplete("destroy");
    }

    $input.autocomplete({
      source: function (request, response) {
        $.ajax({
          url: `https://my-spring-app-ck1f.onrender.com/purchase/autosuggest/${key}`,
          data: { q: request.term || "" },
          success: function (data) {
            const results = Array.isArray(data)
              ? data.map(item => ({ label: item, value: item }))
              : [];
            response(results);
          },
          error: function () {
            response([]);
          }
        });
      },
      minLength: 0,
      appendTo: "#overallModal", // ✅ keep dropdown inside modal
      select: function (event, ui) {
        $input.val(ui.item.value); // ✅ set selected value
        return false; // keep it from overwriting manually
      },
      focus: function (event, ui) {
        $input.val(ui.item.label); // show hovered suggestion
        return false;
      }
    });

    // Show dropdown immediately on focus
    $input.on("focus", function () {
      $(this).autocomplete("search", "");
    });
  });
}

// Re-initialize autocomplete every time modal is shown
$('#overallModal').on('shown.bs.modal', function () {
  setupModalAutoCompleteFilters();
});



// $(document).ready(setupAutoCompleteFilters);


function downloadTemplate(type) {
  const url = type === 'purchased'
    ? 'https://my-spring-app-ck1f.onrender.com/purchase/downloadpurcahsedtemplete'
    : 'https://my-spring-app-ck1f.onrender.com/purchase/downloadstocktemplete';

  fetch(url)
    .then(response => {
      if (!response.ok) throw new Error('Download failed');
      return response.blob();
    })
    .then(blob => {
      const fileName = type === 'purchased' ? 'Purchased_Template.xlsx' : 'Stock_Template.xlsx';

      // Create a link and trigger download
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();

      // Show success popup using SweetAlert2
      Swal.fire({
        icon: 'success',
        title: 'Downloaded!',
        text: `${fileName} has been downloaded.`,
        timer: 2000,
        showConfirmButton: false
      });
    })
    .catch(err => {
      Swal.fire({
        icon: 'error',
        title: 'Download Failed',
        text: err.message
      });
    });
}
function uploadBudget() {
  const file = document.getElementById("budgetFile").files[0];
  if (!file) return;

  const formData = new FormData();
  formData.append("file", file);

  fetch("https://my-spring-app-ck1f.onrender.com/purchase/purcahseupload", {
    method: "POST",
    body: formData
  })
    .then(response => {
      if (!response.ok) throw new Error("Upload failed");
      return response.blob();
    })
    .then(blob => {
      if (!blob || blob.size === 0) {
        showToast("❌ Upload failed or returned empty file.", false);
        return;
      }

      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Purchase_Upload_Result_${new Date().toISOString().split("T")[0]}.xlsx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);

      // ✅ Success UI
      document.getElementById("PurchaseUpdated").innerText = new Date().toLocaleString();
      bootstrap.Modal.getInstance(document.getElementById("budgetModal")).hide();
      showToast("✅ Purchase upload successful. Reloading table...");
      setTimeout(() => buildDashboard(), 1000);
    })
    .catch(() => showToast("❌ Error uploading purchase file.", false));
}
function uploadStock() {
  const file = document.getElementById("stockFile").files[0];
  if (!file) return;

  const formData = new FormData();
  formData.append("file", file);

  fetch("https://my-spring-app-ck1f.onrender.com/purchase/stocksupload", {
    method: "POST",
    body: formData
  })
    .then(response => {
      if (!response.ok) throw new Error("Upload failed");
      return response.blob();
    })
    .then(blob => {
      if (!blob || blob.size === 0) {
        showToast("❌ Upload failed or returned empty file.", false);
        return;
      }

      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Stock_Upload_Result_${new Date().toISOString().split("T")[0]}.xlsx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);

      // ✅ Success UI
      document.getElementById("stockUpdated").innerText = new Date().toLocaleString();
      bootstrap.Modal.getInstance(document.getElementById("stockModal")).hide();
      showToast("✅ Stock upload successful. Reloading table...");
      setTimeout(() => buildDashboard(), 1000);
    })
    .catch(() => showToast("❌ Error uploading stock file.", false));
}

function toggleOverallUpload() {
  const overallBtn = document.getElementById('overallUploadBtn');
  const checkbox = document.getElementById('showOverallUpload');
  const checkboxContainer = checkbox.closest('.form-check');

  // Show the upload button
  overallBtn.classList.remove('d-none');

  // Hide the checkbox itself
  checkboxContainer.classList.add('d-none');
  setupModalAutoCompleteFilters();
}

const formatter = new Intl.NumberFormat('en-IN');

const normalizeHeader = h => h?.toString().toLowerCase().trim().replace(/[\s_]/g, '');
const toNumber = val => isNaN(parseFloat((val || '').toString().replace(/,/g, ''))) ? 0 : Math.round(parseFloat(val.toString().replace(/,/g, '')));
const formatINR = n => '\u20B9' + formatter.format(n);



function openEditModal(id, status, remark, date) {
  document.getElementById('editId').value = id;
  document.getElementById('editStatus').value = status || '';
  document.getElementById('editRemark').value = remark || '';

  // Convert date to YYYY-MM-DD format for input[type=date]
  if (date) {
    const d = new Date(date);
    if (!isNaN(d)) {
      const yyyy = d.getFullYear();
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      const dd = String(d.getDate()).padStart(2, '0');
      document.getElementById('editDate').value = `${yyyy}-${mm}-${dd}`;
    } else {
      document.getElementById('editDate').value = '';
    }
  } else {
    document.getElementById('editDate').value = '';
  }

  new bootstrap.Modal(document.getElementById('editModal')).show();
}
function saveEdit() {
  const id = document.getElementById('editId').value;
  const status = document.getElementById('editStatus').value;
  const remarks = document.getElementById('editRemark').value;
  const date = document.getElementById('editDate').value;

  // if (!id || !status) {
  //   showToast("❗ Please fill required fields (Status).", false);
  //   return;
  // }

  const payload = {
    id: id,
    status: status,
    remarks: remarks
  };

  // Only add `date` if it's filled
  if (date) {
    payload.date = date;
  }

  fetch("https://my-spring-app-ck1f.onrender.com/purchase/statusupdate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  })
    .then(res => res.json())
    .then(json => {
      if (json.result) {
        bootstrap.Modal.getInstance(document.getElementById('editModal')).hide();
        showToast("✅ Status updated!");
        applyFilters();
      } else {
        showToast("❌ Update failed.", false);
      }
    })
    .catch(err => {
      console.error("Error:", err);
      showToast("❌ Error sending data.", false);
    });
}


let filters = {};
let specialFilter = null;
let currentPage = 1;
let totalPages = 1;
let pageSize = 10;
let rawData = [];
let summaryKey = null;
$(document).ready(function () {
  applyFilters();

  $("#limitSelector").on("change", function () {
    pageSize = parseInt($(this).val());
    currentPage = 1;
    applyFilters();
  });

  $("#prevPage").on("click", function () {
    if (currentPage > 1) {
      currentPage--;
      applyFilters();
    }
  });

  $("#nextPage").on("click", function () {
    if (currentPage < totalPages) {
      currentPage++;
      applyFilters();
    }
  });

  $("#searchBtn").on("click", function () {
    currentPage = 1;
    applyFilters();
  });
});
function resetFilters() {
  // Clear all input values
  document.getElementById("categoryFilter").value = "";
  document.getElementById("subcategoryFilter").value = "";
  document.getElementById("supplierFilter").value = "";
  document.getElementById("statusFilter").value = "";
  document.getElementById("filterdate").value = "";
  summaryKey = null;
  currentPage = 1;
  fetchSummaryCards();
  applyFilters();
}

function switchTab(tabId) {
  document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
  document.getElementById(tabId).classList.add('active');

  document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
  const activeBtn = Array.from(document.querySelectorAll('.tab-button'))
    .find(btn => btn.textContent.includes(tabId === 'details' ? 'Detail' : 'Supplier'));
  if (activeBtn) activeBtn.classList.add('active');
}

function applyFilters() {
  const requestData = {
    category: $("#categoryFilter").val()?.trim() || null,
    sub_category: $("#subcategoryFilter").val()?.trim() || null,
    supplier: $("#supplierFilter").val()?.trim() || null,
    status: $("#statusFilter").val()?.trim() || null,
    date: $("#filterdate").val() || null,
    page: currentPage,
    summaryKey: summaryKey || null,
    limit: pageSize
  };

  fetch("https://my-spring-app-ck1f.onrender.com/purchase/search", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(requestData)
  })
    .then(res => res.json())
    .then(json => {
      if (json.result && json.purchaseList) {
        rawData = json.purchaseList;
        totalPages = json.totalPages || 1;
        buildDashboard();
        fetchSummaryCards();
        // ✅ Format date + time: YYYY-MM-DD HH:mm:ss
        const formatDateTime = (d) => {
          const yyyy = d.getFullYear();
          const mm = String(d.getMonth() + 1).padStart(2, '0');
          const dd = String(d.getDate()).padStart(2, '0');

          let hh = d.getHours();
          const min = String(d.getMinutes()).padStart(2, '0');
          const ss = String(d.getSeconds()).padStart(2, '0');
          const ampm = hh >= 12 ? 'pm' : 'am';
          hh = hh % 12;
          hh = hh ? hh : 12; // 0 becomes 12
          hh = String(hh).padStart(2, '0');

          return `${yyyy}-${mm}-${dd} ${hh}:${min}:${ss} ${ampm}`;
        };

        if (json.latest_purchaseupload_at) {
          const purchaseDate = new Date(json.latest_purchaseupload_at);
          document.getElementById('lastPurchaseTime').textContent =
            '' + formatDateTime(purchaseDate);
        }

        if (json.latest_stockupload_at) {
          const stockDate = new Date(json.latest_stockupload_at);
          document.getElementById('lastStockTime').textContent =
            '' + formatDateTime(stockDate);
        }

      } else {
        rawData = [];
        totalPages = 1;
        buildDashboard();
        alert("No data found.");
      }
    })
    .catch(err => {
      console.error("Error:", err);
      alert("❌ Error fetching data.");
    });

  setupAutoCompleteFilters?.(); // optional
}
function buildDashboard() {
  const formatter = new Intl.NumberFormat('en-IN');
  const detailOrder = [
    'category', 'code', 'product_name', 'supplier',
    'budget_qty', 'purchsed_qty',
    'stock_in_hand', 'min_stock_qty', 'max_stock_qty',
    'moq', 'lead_time', 'schedule', 'status', 'remarks', 'date',
    'budget_value', 'purchsed_value', 'stock_in_hand_value'
  ];

  let tableHTML = '<thead><tr><th>S.No</th>' + detailOrder.map(k =>
    `<th>${k.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}</th>`).join('') + '</tr></thead><tbody>';

  const supplierMap = {};

  rawData.forEach((row, idx) => {
    // No normalization: use as-is to preserve field names
    const h = row;
    const sNo = (currentPage - 1) * pageSize + idx + 1;
    const rowId = h['id'] || `row-${idx}`;

    const supplier = h['supplier']?.trim() || 'Unknown';

    // Safely convert to numbers (even if string or null)
    const budVal = parseFloat(h['budget_value']) || 0;
    const purVal = parseFloat(h['purchsed_value']) || 0;
    const stockVal = parseFloat(h['stock_in_hand_value']) || 0;

    if (!supplierMap[supplier]) {
      supplierMap[supplier] = {
        budget: 0, purchased: 0, stock: 0,
        red: 0, orange: 0, green: 0, blue: 0
      };
    }

    supplierMap[supplier].budget += budVal;
    supplierMap[supplier].purchased += purVal;
    supplierMap[supplier].stock += stockVal;

    // Status color grouping
    const status = (h['status'] || '').toLowerCase();
    if (status === 'po pending') supplierMap[supplier].red++;
    else if (status === 'po raised') supplierMap[supplier].orange++;
    else if (status === 'vendor issue') supplierMap[supplier].green++;
    else if (status === 'stock in transit') supplierMap[supplier].blue++;

    // Detail Table
    tableHTML += `<tr><td>${sNo}</td>` + detailOrder.map(k => {
      let val = h[k];
      let displayVal = val;

      if ([
        'budget_qty', 'purchsed_qty', 'stock_in_hand', 'min_stock_qty', 'max_stock_qty',
        'moq', 'lead_time', 'budget_value', 'purchsed_value', 'stock_in_hand_value'
      ].includes(k)) {
        const num = parseFloat(val);
        displayVal = isNaN(num) ? '' : formatter.format(num);
      }

      if (k === 'status' || k === 'remarks') {
        const safeStatus = (h['status'] || '').replace(/'/g, "\\'");
        const safeRemark = (h['remarks'] || '').replace(/'/g, "\\'");
        const safeDate = (h['date'] || '').replace(/'/g, "\\'");
        return `<td class="clickable" onclick="openEditModal('${rowId}', '${safeStatus}', '${safeRemark}', '${safeDate}')">${displayVal || ''}</td>`;
      }

      return `<td>${displayVal || ''}</td>`;
    }).join('') + '</tr>';
  });

  tableHTML += '</tbody>';
  document.getElementById('dataTable').innerHTML = tableHTML;

  // ✅ Pagination Info
  $("#pageInfo").text(`Page ${currentPage} of ${totalPages}`);

  // ✅ Build Supplier Summary Table
  let supplierHTML = '<thead><tr><th>Supplier</th><th>Budget Value</th><th>Purchased Value</th><th>Stock Value</th><th>🔴</th><th>🟠</th><th>🟢</th><th>🔵</th></tr></thead><tbody>';

  Object.entries(supplierMap).forEach(([supplier, data]) => {
    supplierHTML += `
      <tr>
        <td>${supplier}</td>
        <td>${formatter.format(data.budget)}</td>
        <td>${formatter.format(data.purchased)}</td>
        <td>${formatter.format(data.stock)}</td>
        <td>${data.red}</td>
        <td>${data.orange}</td>
        <td>${data.green}</td>
        <td>${data.blue}</td>
      </tr>`;
  });

  supplierHTML += '</tbody>';
  document.getElementById('supplierTable').innerHTML = supplierHTML;
}


function fetchSummaryCards() {
  const requestData = {
    category: $("#categoryFilter").val()?.trim() || null,
    subcategory: $("#subcategoryFilter").val()?.trim() || null,
    supplier: $("#supplierFilter").val()?.trim() || null,
    status: $("#statusFilter").val()?.trim() || null,
    date: $("#filterdate").val() || null,
    summaryKey: summaryKey || null
  };

  fetch("https://my-spring-app-ck1f.onrender.com/purchase/summary", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(requestData)
  })
    .then(response => response.json())
    .then(summary => {
      const card = (label, value, sub = "", variant = "", filterKey = null) => `
        <div class="card ${variant}" ${filterKey ? `onclick="filterBySummary('${filterKey}')"` : ""}>
          <div class="card-title">${label}</div>
          <div class="card-value">${value}</div>
          ${sub ? `<div class="card-subtext">${sub}</div>` : ""}
        </div>
      `;

      const formatINR = (val) =>
        new Intl.NumberFormat("en-IN", {
          style: "currency",
          currency: "INR",
          maximumFractionDigits: 0,
        }).format(val);

      const summaryHTML = [
        card("Total Budget", formatINR(summary.totalBudget), "", "primary"),
        card("Total Purchased", formatINR(summary.totalPurchased), "", "info"),
        card("Difference", formatINR(summary.difference), "", "danger"),
        card("Utilization", `${summary.utilization}%`, "", "warning"),
        card("Unplanned Purchase", formatINR(summary.unplanned), "", "danger", "UNPLANNED"),
        card("Planned Shortfall", formatINR(summary.shortfall), "", "warning", "SHORTFALL"),
        card("Planned vs Actual", `${summary.planVsActual}%`, "", "success"),
        card("Suppliers", summary.suppliers, "", "info"),
        card("Products", summary.products, "", "primary"),
        card("Nil Stock", summary.nilStock, "", "danger", "NIL"),
        card("SKU ≤ Min", summary.skuMin, "", "warning"),
        card("SKU < Min = MOQ", summary.belowMinMOQ, "", "warning"),
        card("Excess Stock", summary.excessStock, "", "info", "EXCESS"),
        card("Stock in Hand Value", formatINR(summary.stockValue), "", "success"),
      ].join("");

      document.getElementById("summaryCards").innerHTML = `<div class="cards">${summaryHTML}</div>`;

      const statusCounts = summary.statusCounts || {};
      document.getElementById('legend').innerHTML = `
        <div class="legend d-flex justify-content-center">
          <div class="legend-row">
            <div class="legend-item red" onclick="filterByStatus('PO Pending')">⛔ PO Pending (${statusCounts['PO Pending'] || 0})</div>
            <div class="legend-item green" onclick="filterByStatus('PO Raised')">🟩 PO Raised (${statusCounts['PO Raised'] || 0})</div>
            <div class="legend-item orange" onclick="filterByStatus('Vendor Issue')">🟨 Vendor Issue (${statusCounts['Vendor Issue'] || 0})</div>
            <div class="legend-item red" onclick="filterByStatus('Payment Issue')">🔴 Payment Issue (${statusCounts['Payment Issue'] || 0})</div>
            <div class="legend-item blue" onclick="filterByStatus('Stock in Transit')">🔹 Stock in Transit (${statusCounts['Stock in Transit'] || 0})</div>
          </div>
        </div>
      `;
    })
    .catch(error => {
      console.error("Failed to fetch summary cards", error);
      alert("❌ Failed to fetch summary cards");
    });
}

function filterBySummary(key) {
  summaryKey = key;
  fetchSummaryCards();
  applyFilters();
}

function filterByStatus(status) {
  document.querySelectorAll(".status-selectable").forEach(el =>
    el.classList.remove("active-status")
  );

  const selected = [...document.querySelectorAll(".status-selectable")]
    .find(el => el.textContent.includes(status));
  if (selected) selected.classList.add("active-status");

  const statusFilter = document.getElementById("statusFilter");
  if (statusFilter) {
    statusFilter.value = status;
    applyFilters();
  }
}
function downloadTemplateReport() {
  const category = document.getElementById("modalCategory")?.value || "";
  const supplier = document.getElementById("modalSupplier")?.value || "";
  const status = document.getElementById("modalStatus")?.value || "";

  const queryParams = new URLSearchParams({ category, supplier, status });
  const url = `https://my-spring-app-ck1f.onrender.com/purchase/download?${queryParams.toString()}`;

  fetch(url)
    .then(response => {
      if (!response.ok) {
        throw new Error("Failed to download Excel.");
      }
      const disposition = response.headers.get("Content-Disposition");
      let filename = "purchase_report.xlsx";

      // Try to extract filename from header if available
      if (disposition && disposition.includes("filename=")) {
        const match = disposition.match(/filename="?([^"]+)"?/);
        if (match?.[1]) {
          filename = match[1];
        }
      }

      return response.blob().then(blob => ({ blob, filename }));
    })
    .then(({ blob, filename }) => {
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url); // Clean up
    })
    .catch(error => {
      console.error("Download error:", error);
      alert("❌ Failed to download Excel file.");
    });
}

function uploadoverall() {
  const input = document.getElementById('excelInput');
  if (!input.files || input.files.length === 0) return;

  const file = input.files[0];
  const formData = new FormData();
  formData.append("file", file);

  fetch("https://my-spring-app-ck1f.onrender.com/purchase/purcahseuploadfile", {
    method: "POST",
    body: formData
  })
    .then(response => {
      if (!response.ok) throw new Error("Upload failed");

      const disposition = response.headers.get("Content-Disposition");
      let filename = "response.xlsx";
      if (disposition && disposition.includes("filename=")) {
        const match = disposition.match(/filename="?([^"]+)"?/);
        if (match?.[1]) {
          filename = match[1];
        }
      }

      return response.blob().then(blob => ({ blob, filename }));
    })
    .then(({ blob, filename }) => {
      // Trigger file download
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);

      // Success toast
      bootstrap.Modal.getInstance(document.getElementById("overallModal")).hide();
      showToast("✅ Overall uploaded!");
      applyFilters(); // if needed
    })
    .catch(() => showToast("❌ Upload error.", false));
}
