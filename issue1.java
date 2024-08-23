private Set<CAGProfileBulkUploadDTO> validateXlsxFile(MultipartFile file) throws IOException {
        Set<CAGProfileBulkUploadDTO> cagProfileBulkUploadDTOs = new HashSet<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                String[] data = new String[row.getPhysicalNumberOfCells()];
                for (int j = 0; j < row.getPhysicalNumberOfCells(); j++) {
                    if (row.getCell(j) == null) {
                        data[j] = "";
                    } else {
                        data[j] = row.getCell(j).toString().trim();
                    }
                }
                CAGProfileBulkUploadDTO cagProfileBulkUpload = createCAGProfileBulkUploadDTO(data);
                validateCAGProfileBulkUploadDTO(cagProfileBulkUpload);
                cagProfileBulkUploadDTOs.add(cagProfileBulkUpload);
            }
        }
        return cagProfileBulkUploadDTOs;
    }

private Set<CAGProfileBulkUploadDTO> validateXlsxFile(MultipartFile file) throws IOException {
    Set<CAGProfileBulkUploadDTO> cagProfileBulkUploadDTOs = new HashSet<>();
    
    try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
        Sheet sheet = workbook.getSheetAt(0);
        
        // Iterate over rows, starting from the first data row
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            
            // Skip null rows
            if (row == null) {
                continue;
            }
            
            String[] data = new String[row.getPhysicalNumberOfCells()];
            
            // Iterate over cells
            for (int j = 0; j < row.getPhysicalNumberOfCells(); j++) {
                Cell cell = row.getCell(j);
                
                // Read cell value as String
                if (cell == null) {
                    data[j] = ""; // or null if you prefer
                } else {
                    data[j] = cell.toString().trim();
                }
            }
            
            // Create and validate DTO
            CAGProfileBulkUploadDTO cagProfileBulkUpload = createCAGProfileBulkUploadDTO(data);
            validateCAGProfileBulkUploadDTO(cagProfileBulkUpload);
            
            cagProfileBulkUploadDTOs.add(cagProfileBulkUpload);
        }
    }
    
    return cagProfileBulkUploadDTOs;
}
