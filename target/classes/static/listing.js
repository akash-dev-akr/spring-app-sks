// Sample static data to populate the table
const sampleData = [
  {
    date: "2025-07-19",
    category: "Raw Material",
    packFormat: "Box",
    section: "A",
    code: "RM001",
    product: "Sugar",
    indQty: 100,
    avaQty: 50,
    reqQty: 70,
    plnQty: 60,
    pacQty: 40,
    disQty: 30,
    recQty: 20
  }
];

const uploadField = (field) => {
  alert(`You clicked upload for ${field}`);
};

const tableBody = document.getElementById("tableBody");

// Populate table
sampleData.forEach((item, index) => {
  const row = document.createElement("tr");
  row.setAttribute("data-index", index);
  row.innerHTML = `
    <td>${item.date}</td>
    <td>${item.category}</td>
    <td>${item.packFormat}</td>
    <td>${item.section}</td>
    <td>${item.code}</td>
    <td>${item.product}</td>
    <td>${item.indQty}</td>
    <td>${item.avaQty}</td>
    <td>${item.reqQty}</td>
    <td>${item.reqQty - item.avaQty}</td>
    <td>${item.indQty - item.reqQty}</td>
    <td>${item.plnQty}</td>
    <td>${item.plnQty - item.reqQty}</td>
    <td>${item.reqQty - item.plnQty}</td>
    <td>${item.pacQty}</td>
    <td>${item.pacQty - item.reqQty}</td>
    <td>${item.reqQty - item.pacQty}</td>
    <td>${item.disQty}</td>
    <td>${item.disQty - item.reqQty}</td>
    <td>${item.reqQty - item.disQty}</td>
    <td>${item.recQty}</td>
    <td>${item.recQty - item.reqQty}</td>
    <td>${item.reqQty - item.recQty}</td>
  `;
  row.onclick = () => openModal(index);
  tableBody.appendChild(row);
});

function openModal(index) {
  const item = sampleData[index];
  const editFields = document.getElementById("editFields");
  editFields.innerHTML = ""; // Clear previous

  const fields = [
    { key: "indQty", label: "IND QTY" },
    { key: "avaQty", label: "AVA QTY" },
    { key: "reqQty", label: "REQ QTY" },
    { key: "plnQty", label: "PLN QTY" },
    { key: "pacQty", label: "PAC QTY" },
    { key: "disQty", label: "DIS QTY" },
    { key: "recQty", label: "REC QTY" }
  ];

  fields.forEach(field => {
    const div = document.createElement("div");
    div.className = "clonable";
    div.innerHTML = `
      <label>${field.label}</label>
      <input type="number" class="field-input" data-label="${field.label}" value="${item[field.key]}" />
    `;
    editFields.appendChild(div);
  });

  document.getElementById("editModal").style.display = "block";
  updateTotal();
}

function closeModal() {
  document.getElementById("editModal").style.display = "none";
}

function addClone() {
  const container = document.getElementById("editFields");
  const clones = container.querySelectorAll(".clonable");

  clones.forEach(clone => {
    const newDiv = clone.cloneNode(true);
    newDiv.querySelector("input").value = "0";
    container.appendChild(newDiv);
  });

  updateTotal();
}

function updateTotal() {
  const inputs = document.querySelectorAll("#editFields input");
  let total = 0;

  inputs.forEach(input => {
    total += Number(input.value || 0);
    input.oninput = updateTotal;
  });

  document.getElementById("totalSum").innerText = total;
}
