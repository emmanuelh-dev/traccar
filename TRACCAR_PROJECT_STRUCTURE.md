# Traccar Project Structure Documentation

## Overview

Traccar is a modern GPS tracking platform built with Java. It provides real-time GPS tracking capabilities with a comprehensive web interface and API for managing devices, geofences, reports, and notifications.

## Project Architecture

### Core Technologies
- **Backend**: Java with JAX-RS (Jersey) for REST API
- **Database**: Support for multiple databases (H2, MySQL, PostgreSQL, SQL Server, etc.)
- **Build System**: Gradle
- **Dependency Injection**: Jakarta Inject (JSR-330)
- **Database Migration**: Liquibase
- **Frontend**: Modern web application with responsive design

## Directory Structure

```
traccar/
├── build.gradle              # Main build configuration
├── settings.gradle           # Gradle settings
├── gradlew/gradlew.bat      # Gradle wrapper scripts
├── LICENSE.txt              # Apache 2.0 License
├── README.md               # Project documentation
├── openapi.yaml            # API specification
├── debug.xml               # Debug configuration
├── bin/                    # Compiled binaries
├── build/                  # Build output directory
├── env/                    # Python environment (for tools)
├── gradle/                 # Gradle configuration
├── schema/                 # Database schema and migrations
├── setup/                  # Installation and setup scripts
├── src/                    # Source code
├── target/                 # Maven target directory (legacy)
├── templates/              # Configuration templates
└── tools/                  # Development and maintenance tools
```

## Source Code Structure (`src/`)

### Main Application (`src/main/java/org/traccar/`)

#### Core Packages

##### 1. **API Layer** (`api/`)
- **Purpose**: REST API endpoints and resources
- **Key Components**:
  - Resource classes for CRUD operations
  - Authentication and authorization handlers
  - Request/response models
  - API filters and interceptors

##### 2. **Storage Layer** (`storage/`)
- **Purpose**: Data persistence and query abstraction
- **Key Components**:
  - `Storage.java` - Abstract storage interface
  - `DatabaseStorage.java` - SQL database implementation
  - `MemoryStorage.java` - In-memory implementation (for testing)
  - `query/` - Query builder and condition classes
- **Query System**:
  - `Condition` interface with implementations (Equals, Compare, Between, And, Or)
  - `Request` class for building database queries
  - `Columns` class for column selection

##### 3. **Model Layer** (`model/`)
- **Purpose**: Data models and entities
- **Key Models**:
  - `Device.java` - GPS tracking devices
  - `Position.java` - GPS position data
  - `User.java` - System users
  - `Geofence.java` - Geographic boundaries
  - `Event.java` - System events and notifications
  - `GeofenceSession.java` - Geofence entry/exit sessions

##### 4. **Session Management** (`session/`)
- **Purpose**: Real-time session and state management
- **Key Components**:
  - `SessionManager.java` - Device session tracking
  - `GeofenceSessionManager.java` - Geofence session handling
  - `cache/CacheManager.java` - Object caching and synchronization

##### 5. **Protocol Support** (`protocol/`)
- **Purpose**: GPS device protocol implementations
- **Components**:
  - Protocol decoders for various GPS device manufacturers
  - Frame decoders for different data formats
  - Message parsers and encoders

##### 6. **Reports** (`reports/`)
- **Purpose**: Report generation and data analysis
- **Components**:
  - `GeofenceSessionReportProvider.java` - Geofence session reports
  - Various report providers for different data types
  - Common utilities for report generation

##### 7. **Configuration** (`config/`)
- **Purpose**: Application configuration management
- **Components**:
  - Configuration keys and validation
  - Type-safe configuration access
  - Environment-specific settings

##### 8. **Helper Utilities** (`helper/`)
- **Purpose**: Common utilities and helper functions
- **Components**:
  - Model utilities
  - Data conversion helpers
  - Common algorithms

##### 9. **Database** (`database/`)
- **Purpose**: Database operations and management
- **Components**:
  - Connection management
  - Migration utilities
  - Statistics and monitoring

##### 10. **Broadcast** (`broadcast/`)
- **Purpose**: Inter-service communication
- **Components**:
  - Message broadcasting between instances
  - Cache invalidation
  - Distributed system coordination

##### 11. **Schedule** (`schedule/`)
- **Purpose**: Scheduled tasks and background jobs
- **Components**:
  - Device inactivity checks
  - Cleanup tasks
  - Periodic maintenance

### Database Schema (`schema/`)

The schema directory contains Liquibase changelog files for database migrations:

```
schema/
├── changelog-3.3.xml       # Legacy migrations
├── changelog-3.5.xml
├── ...
├── changelog-5.11.xml      # Recent migrations
└── ...
```

Each changelog file contains:
- Table creation and modification scripts
- Index definitions
- Data migration scripts
- Version-specific updates

### Key Database Tables

1. **tc_devices** - Device information and configuration
2. **tc_positions** - GPS position data
3. **tc_events** - System events and notifications
4. **tc_users** - User accounts and settings
5. **tc_geofences** - Geofence definitions
6. **tc_geofence_sessions** - Geofence entry/exit tracking
7. **tc_permissions** - User permissions and access control

## Configuration System

### Configuration Files
- **Default**: `traccar.xml` in classpath
- **Custom**: Specified via command line or environment
- **Templates**: Available in `templates/` directory

### Key Configuration Areas
1. **Database Connection**: JDBC settings and connection pooling
2. **Server Settings**: HTTP/HTTPS ports and SSL configuration
3. **Protocol Settings**: Port mappings for different GPS protocols
4. **Security**: Authentication and authorization settings
5. **Notifications**: Email, SMS, and webhook configurations

## Build System

### Gradle Configuration
- **Main Build**: `build.gradle` - Dependencies, plugins, and tasks
- **Dependencies**: Jakarta EE, Jersey, Jackson, Liquibase, etc.
- **Testing**: JUnit 5 with extensive protocol decoder tests
- **Static Analysis**: Checkstyle and FindBugs integration

### Build Targets
- `gradle build` - Complete build with tests
- `gradle run` - Run the application
- `gradle test` - Run unit tests
- `gradle jar` - Create JAR file

## Development Workflow

### Key Development Areas

1. **Adding New Device Protocols**:
   - Create protocol decoder in `protocol/` package
   - Implement frame decoder if needed
   - Add unit tests
   - Update configuration

2. **Adding New Features**:
   - Create/modify models in `model/` package
   - Update storage layer if needed
   - Add API endpoints in `api/` package
   - Update database schema if required

3. **Customizing Reports**:
   - Extend existing report providers in `reports/`
   - Create new report types
   - Add business logic for data processing

### Testing Strategy
- **Unit Tests**: Comprehensive protocol decoder testing
- **Integration Tests**: Database and API testing
- **Protocol Tests**: Real device data validation

## Deployment Options

1. **Standalone JAR**: Self-contained application with embedded server
2. **WAR Deployment**: Deploy to existing application server
3. **Docker**: Containerized deployment with official images
4. **Cloud**: Various cloud platform integrations

## Security Features

1. **Authentication**: User login with password hashing
2. **Authorization**: Role-based access control
3. **HTTPS**: SSL/TLS encryption support
4. **API Security**: Token-based authentication
5. **Input Validation**: Comprehensive input sanitization

## Performance Considerations

1. **Caching**: Multi-level caching for objects and sessions
2. **Database Optimization**: Proper indexing and query optimization
3. **Connection Pooling**: Efficient database connection management
4. **Protocol Efficiency**: Optimized binary protocol parsing
5. **Memory Management**: Careful memory usage in high-throughput scenarios

## Extension Points

### For Developers
1. **Custom Protocols**: Add support for new GPS device types
2. **Custom Reports**: Create specialized reporting features
3. **Custom Notifications**: Implement new notification channels
4. **Custom Computed Attributes**: Add custom device data processing
5. **Custom Web Interface**: Modify or replace the web frontend

### Integration APIs
1. **REST API**: Complete CRUD operations for all entities
2. **WebSocket**: Real-time position updates
3. **Webhooks**: Event-driven integrations
4. **Database Direct**: Direct database access for advanced integrations

## Recent Enhancements

### Geofence Session Tracking
- **GeofenceSessionManager**: Handles geofence entry/exit events
- **GeofenceSession Model**: Tracks time spent in geofences
- **Session Reports**: Generate reports on geofence usage
- **Null Condition Handling**: Improved database query handling for null values

### Storage System Improvements
- **Enhanced Condition System**: Better null value handling in database queries
- **Query Builder**: More flexible and type-safe query construction
- **Storage Abstraction**: Cleaner separation between storage implementations

## Getting Started

### Prerequisites
- Java 11 or higher
- Gradle (or use included wrapper)
- Database server (optional, H2 included for development)

### Quick Start
1. Clone the repository
2. Run `./gradlew build` to build the project
3. Run `./gradlew run` to start the server
4. Access the web interface at `http://localhost:8082`
5. Use default admin credentials: admin/admin

### Development Setup
1. Import project into IDE (IntelliJ IDEA recommended)
2. Configure database connection in `traccar.xml`
3. Run main class `org.traccar.Main`
4. Access web interface for testing

This documentation provides a comprehensive overview of the Traccar project structure and should serve as a reference for understanding the codebase architecture and development workflows.
