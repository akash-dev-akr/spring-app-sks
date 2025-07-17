// scripts.js

// ========== PART 1: Utility & Upload Functions ==========

    function updateOverallTimestamp() {
      const now = new Date().toLocaleString();
      const label = document.getElementById("overallUpdated");
      if (label) label.innerText = now;
    }
    function switchTab(tabId) {
      document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
      document.querySelector(`.tab-button[onclick*="${tabId}"]`).classList.add('active');
      document.getElementById(tabId).classList.add('active');
    }

    function downloadTemplate(type) {
      const url = type === 'purchased'
        ? 'http://localhost:8080/api/purchase/downloadpurcahsedtemplete'
        : 'http://localhost:8080/api/purchase/downloadstocktemplete';
      window.open(url, '_blank');
    }
    function showToast(message, isSuccess = true) {
      const toast = document.getElementById('uploadToast');
      const toastBody = document.getElementById('toastMessage');

      toast.classList.remove('bg-success', 'bg-danger');
      toast.classList.add(isSuccess ? 'bg-success' : 'bg-danger');

      toastBody.innerText = message;
      const bsToast = new bootstrap.Toast(toast);
      bsToast.show();
    }

    function uploadBudget() {
      const file = document.getElementById("budgetFile").files[0];
      if (!file) return;

      const formData = new FormData();
      formData.append("file", file);

      fetch("http://localhost:8080/api/purchase/purcahseupload", {
        method: "POST",
        body: formData
      })
        .then(response => response.blob())
        .then(blob => {
          if (blob.size === 0) {
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

          document.getElementById("PurchaseUpdated").innerText = new Date().toLocaleString();
          bootstrap.Modal.getInstance(document.getElementById("budgetModal")).hide();
          showToast("✅ Purchase upload successful. Reloading table...");

          setTimeout(() => buildDashboard(), 1000); // ✅ Reload just the dashboard
        })
        .catch(() => showToast("❌ Error uploading purchase file.", false));
    }

    function uploadStock() {
      const file = document.getElementById("stockFile").files[0];
      if (!file) return;

      const formData = new FormData();
      formData.append("file", file);

      fetch("http://localhost:8080/api/purchase/stocksupload", {
        method: "POST",
        body: formData
      })
        .then(response => response.blob())
        .then(blob => {
          if (blob.size === 0) {
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

          document.getElementById("stockUpdated").innerText = new Date().toLocaleString();
          bootstrap.Modal.getInstance(document.getElementById("stockModal")).hide();
          showToast("✅ Stock upload successful. Reloading table...");

          setTimeout(() => buildDashboard(), 1000); // ✅ Reload just the table
        })
        .catch(() => showToast("❌ Error uploading stock file.", false));
    }


    function uploadExcel() {
      const input = document.getElementById('excelInput');
      if (!input.files || input.files.length === 0) return;
      const file = input.files[0];
      const formData = new FormData();
      formData.append("file", file);
      fetch("http://localhost:8080/api/purchase/upload", {
        method: "POST",
        body: formData
      })
        .then(res => res.json())
        .then(json => {
          if (json.result) {
            bootstrap.Modal.getInstance(document.getElementById("overallModal")).hide();
            showToast("✅ Overall uploaded!");
            loadDashboard(); // optional reload
          } else {
            showToast("❌ Upload failed.", false);
          }
        })
        .catch(() => showToast("❌ Upload error.", false));
    }

    let rawData = [], filters = {}, specialFilter = null;
    const formatter = new Intl.NumberFormat('en-IN');

    const normalizeHeader = h => h?.toString().toLowerCase().trim().replace(/[\s_]/g, '');
    const toNumber = val => isNaN(parseFloat((val || '').toString().replace(/,/g, ''))) ? 0 : Math.round(parseFloat(val.toString().replace(/,/g, '')));
    const formatINR = n => '\u20B9' + formatter.format(n);

    function switchTab(tabId) {
      document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
      document.querySelector(`.tab-button[onclick*="${tabId}"]`).classList.add('active');
      document.getElementById(tabId).classList.add('active');
    }

    function resetFilters() {
      filters = {};
      specialFilter = null;
      document.querySelectorAll('.filter-select').forEach(sel => sel.value = '');
      document.getElementById('fromDate').value = '';
      document.getElementById('toDate').value = '';
      loadDashboard();
    }


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

      if (!id || !status || !date) {
        showToast("❗ Please fill all fields including date.", false);
        return;
      }

      fetch("http://localhost:8080/api/purchase/statusupdate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: id,
          status: status,
          remarks: remarks,
          date: date
        })
      })
        .then(res => res.json())
        .then(json => {
          if (json.result) {
            bootstrap.Modal.getInstance(document.getElementById('editModal')).hide();
            showToast("✅ Status updated!");
            loadDashboard();
          } else {
            showToast("❌ Update failed.", false);
          }
        })
        .catch(err => {
          console.error("Error:", err);
          showToast("❌ Error sending data.", false);
        });
    }



    function loadDashboard() {
      fetch("http://localhost:8080/api/purchase/search", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}) // 👈 No dates sent
      }).then(res => res.json()).then(json => {
        if (json.result && json.purchaseList) {
          rawData = json.purchaseList;
          buildFilters(rawData);
          buildDashboard();
        } else {
          alert("No data found.");
        }
      }).catch(err => {
        console.error("Error:", err);
        alert("❌ Error fetching data.");
      });
    }

    function buildFilters(data) {
      const suppliers = new Set(), statuses = new Set();
      const categories = new Set(), subcategories = new Set();

      data.forEach(row => {
        const h = {};
        for (const key in row) h[normalizeHeader(key)] = row[key];
        if (h['supplier']) suppliers.add(h['supplier']);
        if (h['status']) statuses.add(h['status']);
        if (h['category']) categories.add(h['category']);
        if (h['subcategory']) subcategories.add(h['subcategory']);
      });

      const filterBar = document.getElementById('filters');
      filterBar.innerHTML = `
    ${createFilterSelect('category', [...categories])}
    ${createFilterSelect('sub_category', [...subcategories])}
    ${createFilterSelect('supplier', [...suppliers])}
    ${createFilterSelect('status', [...statuses])}
    <input type="date" id="fromDate" placeholder="From Date" />
    <input type="date" id="toDate" placeholder="To Date" /><button class="btn btn-secondary custom-btn" onclick="resetFilters()">Reset</button>
 

  `;
      // <button class="btn btn-primary custom-btn" onclick="applyDateFilter()">Search</button>
      // 
      document.querySelectorAll('.filter-select').forEach(select => {
        select.addEventListener('change', () => {
          filters[select.dataset.key] = select.value;
          buildDashboard();
        });
      });
    }

    function applyDateFilter() {
      const from = document.getElementById("fromDate").value;
      const to = document.getElementById("toDate").value;

      const filtered = rawData.filter(row => {
        const date = new Date(row.date || row.Date || row.DATE || '');
        if (isNaN(date)) return false;

        let pass = true;

        if (from) {
          const fromDate = new Date(from);
          if (date < fromDate) pass = false;
        }

        if (to) {
          const toDate = new Date(to);
          if (date > toDate) pass = false;
        }

        return pass;
      });

      buildFilters(filtered); // Rebuild filters from filtered data
      loadDashboard();
    }

    function createFilterSelect(key, values) {
      let label = key.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
      let html = `<select class="filter-select" data-key="${key}">`;
      html += `<option value="">All ${label}</option>`;
      values.sort().forEach(v => html += `<option value="${v}">${v}</option>`);
      html += '</select>';
      return html;
    }
    function filterByStatus(status) {
      filters['status'] = status;
      buildDashboard();
    }


    function applyCardFilter(filterKey) {
      specialFilter = filterKey;
      buildDashboard();
    }

    function applyLegendFilter(color) {
      specialFilter = color;
      buildDashboard();
    }

    function buildDashboard() {
      const supplierSummary = {};
      let tableHTML = '', supplierTable = '';
      const detailOrder = [
        'category', 'code', 'product_name', 'supplier',
        'budget_qty', 'budget_value', 'purcahsed_qty', 'purcahsed_value',
        'stock_in_hand',
        'stock_in_hand_value', 'min_stock_qty', 'max_stock_qty',
        'moq', 'lead_time', 'schedule', 'status', 'remarks', 'date'
      ];
      const legendCounts = { red: 0, orange: 0, green: 0, blue: 0 };
      let purchasedValue = 0, budgetValue = 0, stockValue = 0, belowMinMOQ = 0, nilStock = 0, shortValue = 0, unplannedValue = 0, plannedQty = 0, actualQty = 0;
      const suppliers = new Set(), products = new Set();
      tableHTML += '<tr>' + detailOrder.map(k => `<th>${k.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}</th>`).join('') + '</tr>';
      const statusCounts = {
        "PO Pending": 0,
        "PO Raised": 0,
        "Vendor Issue": 0,
        "Payment Issue": 0,
        "Stock in Transit": 0
      };

      rawData.forEach((row, idx) => {
        const h = {};
        for (const key in row) h[normalizeHeader(key)] = row[key];
        const status = h['status']?.trim();
        if (statusCounts[status] !== undefined) statusCounts[status]++;
        const stock = toNumber(h['stockinhand']);
        const min = toNumber(h['minstockqty']);
        const max = toNumber(h['maxstockqty']);
        const moq = toNumber(h['moq']);
        const purVal = toNumber(h['purchasedvalue'] || h['purcahsedvalue']);
        const budVal = toNumber(h['budgetvalue']);
        const budQty = toNumber(h['budgetqty']);
        const purQty = toNumber(h['purchasedqty'] || h['purcahsedqty']);
        const rate = budQty ? budVal / budQty : 0;
        const supplier = h['supplier'] || 'Unknown';
        const rowClass = stock === 0 ? 'red' : (stock < min ? 'orange' : (stock >= moq && stock <= max ? 'green' : (stock > max ? 'blue' : '')));
        const rowId = h['id'] || `row-${idx}`;

        if (!filters['supplier'] || filters['supplier'] === supplier) {
          if (!supplierSummary[supplier]) supplierSummary[supplier] = { red: 0, orange: 0, green: 0, blue: 0, budget: 0, purchase: 0, stock: 0 };
          supplierSummary[supplier][rowClass]++;
          supplierSummary[supplier].budget += budVal;
          supplierSummary[supplier].purchase += purVal;
          supplierSummary[supplier].stock += Math.round(stock * rate);
        }






        if ((!filters['supplier'] || h['supplier'] === filters['supplier']) &&
          (!filters['status'] || h['status'] === filters['status'])) {

          if (specialFilter === 'red' && stock !== 0) return;
          if (specialFilter === 'orange' && !(stock < min)) return;
          if (specialFilter === 'green' && !(stock >= moq && stock <= max)) return;
          if (specialFilter === 'blue' && !(stock > max)) return;
          if (specialFilter === 'unplanned' && !(budQty === 0 && purVal > 0)) return;
          if (specialFilter === 'shortfall' && !(purQty < budQty)) return;

          purchasedValue += purVal;
          budgetValue += budVal;
          stockValue += Math.round(stock * rate);
          plannedQty += budQty;
          actualQty += purQty;
          if (stock <= 0) legendCounts.red++, nilStock++;
          else if (stock < min) legendCounts.orange++, belowMinMOQ++;
          else if (stock >= moq && stock <= max) legendCounts.green++;
          else if (stock > max) legendCounts.blue++;
          if (budQty === 0 && purVal > 0) unplannedValue += purVal;
          if (purQty < budQty) shortValue += Math.round((budQty - purQty) * rate);
          if (h['supplier']) suppliers.add(h['supplier']);
          if (h['code']) products.add(h['code']);

          tableHTML += `<tr class="${rowClass}">` + detailOrder.map(k => {
            let val = h[normalizeHeader(k)];
            let displayVal = val;


            // Format numeric columns
            if (['budgetqty', 'purchasedqty', 'stockinhand', 'minstockqty', 'maxstockqty', 'moq', 'leadtime', 'budgetvalue', 'purchasedvalue', 'stockinhandvalue'].includes(k)) {
              const num = toNumber(val);
              displayVal = isNaN(num) ? '' : formatter.format(num);
            }

            // Editable columns
            if (k === 'status' || k === 'remarks') {
              const safeStatus = (h['status'] || '').replace(/'/g, "\\'");
              const safeRemark = (h['remarks'] || '').replace(/'/g, "\\'");
              const safeDate = (h['date'] || '').replace(/'/g, "\\'");
              return `<td class="clickable" onclick="openEditModal('${rowId}', '${safeStatus}', '${safeRemark}', '${safeDate}')">${displayVal || ''}</td>`;
            }


            // Default cell rendering
            return `<td>${displayVal || ''}</td>`;
          }).join('') + '</tr>';
        }
      });
      const utilization = budgetValue ? Math.round(purchasedValue / budgetValue * 100) : 0;
      const planVsActual = plannedQty ? Math.round(actualQty / plannedQty * 100) : 0;


      const shortPct = budgetValue ? Math.round(shortValue / budgetValue * 100) : 0;

      // NEW METRICS
      // const minStockValue = rawData.reduce((sum, row) => {
      //   const h = {};
      //   for (const key in row) h[normalizeHeader(key)] = row[key];
      //   const rate = toNumber(h['budgetqty']) ? toNumber(h['budgetvalue']) / toNumber(h['budgetqty']) : 0;
      //   return sum + toNumber(h['minstockqty']) * rate;
      // }, 0);



      const minStockValue = rawData.filter(row => {
        const h = {};
        for (const key in row) h[normalizeHeader(key)] = row[key];
        const stock = toNumber(h['stockinhand']);
        const min = toNumber(h['minstockqty']);
        return stock < min;
      }).length;



            const ma  = rawData.filter(row => {
        const h = {};
        for (const key in row) h[normalizeHeader(key)] = row[key];
        const stock = toNumber(h['stockinhand']);
        const pqt = toNumber(h['purchasedqty']);
        return stock + pqt;
      }).length;

      const skuMinimum = rawData.filter(row => {
        const h = {};
        for (const key in row) h[normalizeHeader(key)] = row[key];
        const stock = toNumber(h['stockinhand']);
        const min = toNumber(h['minstockqty']);
        return stock < min;
      }).length;

      const excessStock = rawData.filter(row => {
        const h = {};
        for (const key in row) h[normalizeHeader(key)] = row[key];
        const stock = toNumber(h['stockinhand']);
        const max = toNumber(h['maxstockqty']);
        return stock > max;
      }).length;

      const totalStockInHandValue = rawData.reduce((sum, row) => {
        const h = {};
        for (const key in row) h[normalizeHeader(key)] = row[key];
        return sum + toNumber(h['stockinhandvalue']);
      }, 0);

      const card = (label, value, filterKey = null) =>
        `<div class="card" onclick="applyCardFilter('${filterKey || ''}')"><h3>${label}</h3><span>${value}</span></div>`;

      const row1 =
        card("Total Budget", formatINR(budgetValue)) +
        card("Total Purchased", formatINR(purchasedValue)) +
        card("Difference", formatINR(budgetValue - purchasedValue)) +
        card("Utilization", `${utilization}%`) +
        card("Unplanned Purchase", formatINR(unplannedValue), 'unplanned') +
        card("Planned Shortfall", `${formatINR(shortValue)}<br>${shortPct}%`, 'shortfall') +
        card("Planned vs Actual", `${planVsActual}%`);

      const row2 =
        card("Suppliers", suppliers.size) +
        card("Products", products.size) +
        card("Nil Stock", nilStock, 'red') +
        card("SKU < = Min", skuMinimum) +
        card("SKU < Min = MOQ", belowMinMOQ, 'orange') +

        card("Excess Stock", excessStock, 'blue') +
        // card("Minimum Stock Value", formatINR(minStockValue)) +
        card("Stock in Hand Value", formatINR(totalStockInHandValue));

      document.getElementById('summaryCards').innerHTML = `<div class="d-flex flex-wrap gap-3 justify-content-center mb-3">${row1}</div>
<div class="d-flex flex-wrap gap-3 justify-content-center">${row2}</div>`;


      document.getElementById('dataTable').innerHTML = tableHTML;
document.getElementById('legend').innerHTML = `
  <div class="legend d-flex justify-content-center">
    <div class="legend-row">
      <div class="legend-item red" onclick="filterByStatus('PO Pending')">⛔ PO Pending (${statusCounts['PO Pending']})</div>
      <div class="legend-item green" onclick="filterByStatus('PO Raised')">🟩 PO Raised (${statusCounts['PO Raised']})</div>
      <div class="legend-item orange" onclick="filterByStatus('Vendor Issue')">🟨 Vendor Issue (${statusCounts['Vendor Issue']})</div>
      <div class="legend-item red" onclick="filterByStatus('Payment Issue')">🔴 Payment Issue (${statusCounts['Payment Issue']})</div>
      <div class="legend-item blue" onclick="filterByStatus('Stock in Transit')">🔹 Stock in Transit (${statusCounts['Stock in Transit']})</div>
    </div>
  </div>
`;


//   <div class="legend-row">
//     <div class="legend-item red" onclick="applyLegendFilter('red')">🔴 Nil Stock (${legendCounts.red})</div>
//     <div class="legend-item orange" onclick="applyLegendFilter('orange')">🟠 Low Stock (${legendCounts.orange})</div>
//     <div class="legend-item green" onclick="applyLegendFilter('green')">🟢 MOQ Matched (${legendCounts.green})</div>
//     <div class="legend-item blue" onclick="applyLegendFilter('blue')">🔵 Excess Stock (${legendCounts.blue})</div>
//   </div>

      supplierTable = '<tr><th>Supplier</th><th>Budget Value</th><th>Purchased Value</th><th>Stock Value</th><th>🔴</th><th>🟠</th><th>🟢</th><th>🔵</th></tr>';
      Object.entries(supplierSummary).forEach(([name, stats]) => {
        supplierTable += `<tr><td>${name}</td><td>${formatINR(stats.budget)}</td><td>${formatINR(stats.purchase)}</td><td>${formatINR(stats.stock)}</td><td>${stats.red}</td><td>${stats.orange}</td><td>${stats.green}</td><td>${stats.blue}</td></tr>`;
      });
      document.getElementById('supplierTable').innerHTML = supplierTable;
    }


    // Load data on page load
    window.onload = loadDashboard;